package dev.springdrop.kernel.log;

import dev.springdrop.kernel.cron.CronJob;
import org.springframework.stereotype.Component;

/** On each cron run, removes the database log's oldest events beyond the number the logging settings keep. */
@Component
public class DatabaseLogTrimmer implements CronJob {

    private final ActivityLog log;
    private final DatabaseLog database;

    public DatabaseLogTrimmer(ActivityLog log, DatabaseLog database) {
        this.log = log;
        this.database = database;
    }

    @Override
    public String id() {
        return "dblog";
    }

    @Override
    public void run() {
        int rows = log.settings().databaseLogRows();
        if (rows > 0) {
            database.keepNewest(rows);
        }
    }
}
