# Activity log

The activity log records what happens on the site: actions applied, accounts blocked,
failures. It is the site's own log, apart from the application log Spring Boot writes.

## Logging an event

```java
activityLog.channel("action").notice("{action} was applied to {label}.",
        Map.of("action", "Publish content", "label", "Spring schedule"));
```

`ActivityLog.channel(type)` gives a `LogChannel` whose `error`, `warning`, `notice`, and
`info` methods log at that severity. `ActivityLog.log(type, severity, message, variables)`
logs at any of the eight RFC 5424 severities in `Severity`, from `EMERGENCY` (0) to
`DEBUG` (7).

The message keeps its `{name}` placeholders and the variables are stored apart from it.
`LogEntry.formatted()` fills them in, leaving a placeholder that has no variable as it is.

Each event is stamped with:

| Field | Value |
| --- | --- |
| `uid` | the signed-in account's id, or 0 |
| `location` | the address requested, empty outside a request |
| `referer` | the `Referer` header, empty when there is none |
| `hostname` | the client address |
| `timestamp` | when it was logged |

## Backends

A `LogBackend` bean writes events somewhere. The config object `system.logging`
(`LoggingSettings`) lists the backends in use by id, `database` alone by default, and the
[logging settings](site-settings.md#logging-and-errors) choose them. A
backend that fails to write is reported to the application log, and the others still get
the event.

| Id | Writes to |
| --- | --- |
| `database` | the `activity_log` table, which the log viewer reads |
| `syslog` | a syslog server over UDP, `springdrop.logging.syslog.host` and `.port` (`localhost:514` by default), as RFC 5424 messages from the `local0` facility with the event type as the message id |

`DatabaseLog` also reads the table back: `page(filter, offset, limit)` newest first,
`count(filter)`, `find(id)`, `types()`, `keepNewest(rows)`, and `clear()`. On each cron run,
`DatabaseLogTrimmer` removes the oldest events beyond the number the logging settings keep. A `LogFilter` narrows to one type,
one severity, or both.

## The log viewer

`/admin/reports/dblog` lists the database log, newest first, 50 a page, with Type and
Severity filters. Each row shows the severity as a badge, the type, the date, the message
cut to 56 characters and linked to its detail page, and the account. The detail page,
`/admin/reports/dblog/event/{id}`, shows every field. **Clear log messages** asks for
confirmation and then empties the table. The pages need `access site reports`.
