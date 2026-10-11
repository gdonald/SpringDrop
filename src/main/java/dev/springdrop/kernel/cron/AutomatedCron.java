package dev.springdrop.kernel.cron;

import dev.springdrop.kernel.config.ConfigStore;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Decides whether a page request should start a cron run, from the interval set and the last run. */
@Component
public class AutomatedCron {

    private final ConfigStore config;
    private final CronRunner cron;
    private final Clock clock;

    public AutomatedCron(ConfigStore config, CronRunner cron, Clock clock) {
        this.config = config;
        this.cron = cron;
        this.clock = clock;
    }

    public AutomatedCronSettings settings() {
        return config.read(AutomatedCronSettings.CONFIG_NAME, AutomatedCronSettings.class,
                AutomatedCronSettings.DEFAULTS);
    }

    public void save(AutomatedCronSettings settings) {
        config.save(AutomatedCronSettings.CONFIG_NAME, settings);
    }

    /** Whether automated cron is on and its interval has passed since the last run, or cron never ran. */
    public boolean isDue() {
        AutomatedCronSettings settings = settings();
        if (!settings.enabled()) {
            return false;
        }
        Optional<Instant> last = cron.lastRun();
        return last.isEmpty() || !last.get().plus(settings.interval()).isAfter(clock.instant());
    }
}
