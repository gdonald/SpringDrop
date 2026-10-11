package dev.springdrop.kernel.queue;

import java.time.Duration;

/**
 * Processes the items of one queue during cron. A worker is a plugin of this
 * type whose id is the name of the queue it works. Throwing from
 * {@link #process} fails the item: it is retried after {@link #retryDelay},
 * doubled on each further failure, and quarantined once it has failed
 * {@link #maxAttempts} times.
 */
public interface QueueWorker {

    void process(QueueItem item);

    /** How long one cron run spends on this queue before leaving the rest for the next run. */
    default Duration timeBudget() {
        return Duration.ofSeconds(15);
    }

    default int maxAttempts() {
        return 3;
    }

    default Duration retryDelay() {
        return Duration.ofMinutes(1);
    }
}
