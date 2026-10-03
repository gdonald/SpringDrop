package dev.springdrop.kernel.file;

import dev.springdrop.kernel.cron.CronJob;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Removes temporary files nothing uses once they are older than the grace
 * period, six hours unless {@code springdrop.files.temporary-max-age} says
 * otherwise. The period lets an upload sit in a form being filled in.
 */
@Component
public class TemporaryFileCollector implements CronJob {

    private final FileService files;
    private final FileUsageService usage;
    private final Duration maxAge;

    public TemporaryFileCollector(FileService files, FileUsageService usage,
            @Value("${springdrop.files.temporary-max-age:PT6H}") Duration maxAge) {
        this.files = files;
        this.usage = usage;
        this.maxAge = maxAge;
    }

    @Override
    public String id() {
        return "file.temporary";
    }

    @Override
    public void run() {
        for (ManagedFile stale : files.temporaryOlderThan(maxAge)) {
            if (usage.total(stale.id()) == 0) {
                files.delete(stale.id());
            }
        }
    }
}
