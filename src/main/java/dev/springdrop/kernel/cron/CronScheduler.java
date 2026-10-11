package dev.springdrop.kernel.cron;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs cron on the schedule {@code springdrop.cron.schedule} names, a Spring
 * cron expression. The expression {@code -} turns the schedule off, leaving
 * cron to the external trigger and automated cron.
 */
@Component
@EnableScheduling
public class CronScheduler {

    public static final String SCHEDULE_PROPERTY = "springdrop.cron.schedule";

    private final CronRunner cron;

    public CronScheduler(CronRunner cron) {
        this.cron = cron;
    }

    @Scheduled(cron = "${" + SCHEDULE_PROPERTY + "}")
    public void runOnSchedule() {
        cron.run();
    }
}
