package dev.springdrop.kernel.cron;

/**
 * Housekeeping a site does now and then, such as removing old temporary files.
 * A module adds a job by registering a bean of this type, and {@link CronRunner}
 * runs every job in id order.
 */
public interface CronJob {

    String id();

    void run();
}
