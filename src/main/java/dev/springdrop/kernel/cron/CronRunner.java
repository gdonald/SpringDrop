package dev.springdrop.kernel.cron;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** Runs every cron job the site has, in id order. */
@Component
public class CronRunner {

    private final List<CronJob> jobs;

    public CronRunner(List<CronJob> jobs) {
        this.jobs = jobs.stream().sorted(Comparator.comparing(CronJob::id)).toList();
    }

    public List<String> jobIds() {
        return jobs.stream().map(CronJob::id).toList();
    }

    public void run() {
        jobs.forEach(CronJob::run);
    }
}
