package dev.springdrop.kernel.queue;

import dev.springdrop.kernel.cron.CronJob;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The cron job that works the queues: for each worker, in queue name order, it
 * claims and processes items until the queue is empty or the worker's time
 * budget is spent. A processed item is deleted and a failed one is recorded
 * as a failure, to be retried or quarantined.
 */
@Component
public class QueueProcessor implements CronJob {

    /** How long a claimed item stays leased, so one whose run died is claimable again after it. */
    public static final Duration LEASE = Duration.ofHours(1);

    private final PluginRegistry plugins;
    private final QueueService queues;
    private final Clock clock;

    public QueueProcessor(PluginRegistry plugins, QueueService queues, Clock clock) {
        this.plugins = plugins;
        this.queues = queues;
        this.clock = clock;
    }

    @Override
    public String id() {
        return "queue";
    }

    @Override
    public void run() {
        PluginManager<QueueWorker> workers = plugins.managerFor(QueueWorker.class);
        workers.ids().stream().sorted().forEach(queue -> work(queue, workers.get(queue)));
    }

    private void work(String queue, QueueWorker worker) {
        Instant deadline = clock.instant().plus(worker.timeBudget());
        while (clock.instant().isBefore(deadline)) {
            Optional<QueueItem> claimed = queues.claim(queue, LEASE);
            if (claimed.isEmpty()) {
                return;
            }
            process(worker, claimed.get());
        }
    }

    private void process(QueueWorker worker, QueueItem item) {
        try {
            worker.process(item);
            queues.delete(item);
        } catch (RuntimeException failure) {
            queues.fail(item, failure.toString(), worker.maxAttempts(), worker.retryDelay());
        }
    }
}
