# Cron and queues

Cron runs the site's housekeeping: removing old temporary files, indexing content for
search, and working the queues. A module adds housekeeping by registering a `CronJob`
bean, and work that is too slow for a request by adding items to a queue.

## Jobs

A `CronJob` has an `id()` and a `run()`. `CronRunner.run()` runs every `CronJob` bean in
id order. A job that throws is logged and the jobs after it still run.

| Job id | What it does |
| --- | --- |
| `file.temporary` | Deletes unused temporary files older than `springdrop.files.temporary-max-age`. |
| `queue` | Works the queues (see below). |
| `search.index` | Indexes up to 100 entities waiting to be indexed. |

## The lock and the last run

A run holds the `cron` lock from `LockService` for its whole length, so two triggers
never run the jobs at once. `run()` gives `false` and runs nothing when another run holds
the lock. The lock's lease is 15 minutes, so a run that dies without releasing it blocks
cron for at most that long.

A finished run records the time in the state store (`system.cron_last`).
`CronRunner.lastRun()` gives it.

`LockService` keeps its locks in the `semaphore` table and serves any work that must not
overlap across processes: `acquire(name, lease)` gives an owner token, or empty when the
lock is held, and `release(name, owner)` frees it.

## Triggers

Cron starts in three ways.

- **Schedule.** `CronScheduler` runs cron on the Spring cron expression in
  `springdrop.cron.schedule`, the top of every hour by default. The expression `-` turns
  the schedule off. The test suite turns it off in `src/test/resources/config/application.yml`.
- **Cron URL.** A request for `/cron/{key}` runs cron. The key is a random secret made the
  first time it is asked for and kept in the state store (`system.cron_key`). The answer
  is 204 when cron ran, 409 when another run holds the lock, and 403 for a wrong key.
- **Automated cron.** When automated cron is on, a request that finishes after the
  interval has passed since the last run starts a cron run on the application task
  executor, so the visitor does not wait for it. The interval is the config object
  `automated_cron.settings` (`intervalSeconds`, zero for off, which is the default).

The cron page, `/admin/config/system/cron`, shows when cron last ran, the cron URL, and
the jobs, with a **Run cron** button. The performance page,
`/admin/config/development/performance`, sets the automated cron interval. Both need
`administer site configuration`.

## Queues

`QueueService` is a reliable queue over the `queue` table.

- `enqueue(queue, data)` adds an item, its data stored as JSON.
- `claim(queue, lease)` leases the oldest ready item. A leased item is not claimed again
  until the lease runs out, so an item whose claimer died is picked up later. Concurrent
  claimers skip each other's locked rows.
- `delete(item)` removes a processed item. `release(item)` hands it back unprocessed.
- `fail(item, error, maxAttempts, retryDelay)` records a failure. The item waits
  `retryDelay`, doubled for each earlier failure, before it can be claimed again. Once it
  has failed `maxAttempts` times it is quarantined: kept, with its last error, but never
  claimed. `quarantined(queue)` lists those items.

A queue is worked by a `QueueWorker` plugin whose plugin id is the queue name:

```java
@SpringDropPlugin(id = "mail.digest", type = QueueWorker.class)
class DigestWorker implements QueueWorker {
    @Override
    public void process(QueueItem item) { ... }
}
```

The `queue` cron job works each queue in name order. It claims and processes items until
the queue is empty or the worker's `timeBudget()` (15 seconds unless the worker says
otherwise) is spent, deleting each processed item and failing each one whose `process`
throws. A worker sets `maxAttempts()` (3) and `retryDelay()` (one minute) the same way.
