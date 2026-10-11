package dev.springdrop.kernel.cron;

import java.time.Duration;

/**
 * The automated cron settings, stored as the config object
 * {@code automated_cron.settings}: how many seconds after the last run a page
 * request starts the next one. Zero turns automated cron off, which is the
 * default.
 */
public record AutomatedCronSettings(long intervalSeconds) {

    public static final String CONFIG_NAME = "automated_cron.settings";

    public static final AutomatedCronSettings DEFAULTS = new AutomatedCronSettings(0);

    public boolean enabled() {
        return intervalSeconds > 0;
    }

    public Duration interval() {
        return Duration.ofSeconds(intervalSeconds);
    }
}
