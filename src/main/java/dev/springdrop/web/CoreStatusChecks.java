package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.file.FileSystemProperties;
import dev.springdrop.kernel.security.TrustedHostProperties;
import dev.springdrop.kernel.status.Requirement;
import dev.springdrop.kernel.status.RequirementSeverity;
import dev.springdrop.kernel.status.StatusCheck;
import dev.springdrop.kernel.user.UserSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.springframework.cache.CacheManager;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * The lines core adds to the status report: the database and whether its
 * schema is up to date, when cron last ran, the cache, whether the file
 * directories can be written, the mail server, and the settings that bear on
 * the site's security.
 */
@Component
public class CoreStatusChecks implements StatusCheck {

    /** How long after cron last ran the report warns, as Drupal does. */
    public static final Duration CRON_WARNING_AGE = Duration.ofDays(2);

    /** How long after cron last ran the report calls it an error. */
    public static final Duration CRON_ERROR_AGE = Duration.ofDays(14);

    private final DSLContext dsl;
    private final Flyway flyway;
    private final CronRunner cron;
    private final CacheManager caches;
    private final FileSystemProperties files;
    private final Environment environment;
    private final TrustedHostProperties trustedHosts;
    private final ConfigStore config;
    private final Clock clock;

    public CoreStatusChecks(DSLContext dsl, Flyway flyway, CronRunner cron, CacheManager caches,
            FileSystemProperties files, Environment environment, TrustedHostProperties trustedHosts,
            ConfigStore config, Clock clock) {
        this.dsl = dsl;
        this.flyway = flyway;
        this.cron = cron;
        this.caches = caches;
        this.files = files;
        this.environment = environment;
        this.trustedHosts = trustedHosts;
        this.config = config;
        this.clock = clock;
    }

    @Override
    public List<Requirement> requirements() {
        return List.of(database(), updates(), cron(), cache(),
                directory("Public files directory", Path.of(files.publicPath())),
                directory("Private files directory", Path.of(files.privatePath())),
                directory("Temporary directory", Path.of(System.getProperty("java.io.tmpdir"))),
                mail(), trustedHosts(), registration());
    }

    private Requirement database() {
        try {
            String version = String.valueOf(dsl.fetchValue("select version()"));
            String[] words = version.split(" ");
            return Requirement.of("Database", words.length > 1 ? words[0] + " " + words[1] : version,
                    "", RequirementSeverity.OK);
        } catch (DataAccessException failure) {
            return Requirement.of("Database", "Unreachable",
                    "The site cannot reach its database: " + failure.getMessage(), RequirementSeverity.ERROR);
        }
    }

    private Requirement updates() {
        int pending = flyway.info().pending().length;
        if (pending == 0) {
            return Requirement.of("Database updates", "Up to date", "", RequirementSeverity.OK);
        }
        return Requirement.of("Database updates", "Out of date", pending
                + (pending == 1 ? " database update has" : " database updates have") + " not been applied.",
                RequirementSeverity.ERROR);
    }

    private Requirement cron() {
        Optional<Instant> lastRun = cron.lastRun();
        if (lastRun.isEmpty()) {
            return Requirement.of("Cron maintenance tasks", "Never run", "Cron removes old temporary files, "
                    + "indexes content for search, and works the queues.", RequirementSeverity.ERROR)
                    .linkedTo("Run cron", CronController.PATH);
        }
        Duration age = Duration.between(lastRun.get(), clock.instant());
        RequirementSeverity severity = age.compareTo(CRON_ERROR_AGE) >= 0 ? RequirementSeverity.ERROR
                : age.compareTo(CRON_WARNING_AGE) >= 0 ? RequirementSeverity.WARNING : RequirementSeverity.OK;
        return Requirement.of("Cron maintenance tasks", "Last run " + ago(age) + " ago",
                severity == RequirementSeverity.OK ? "" : "Cron has not run for " + ago(age) + ".", severity)
                .linkedTo("Run cron", CronController.PATH);
    }

    private Requirement cache() {
        return Requirement.of("Cache backend", caches.getClass().getSimpleName(),
                "Caches: " + String.join(", ", caches.getCacheNames()) + ".", RequirementSeverity.INFO);
    }

    private static Requirement directory(String title, Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException failure) {
            return Requirement.of(title, "Not writable", "The directory " + path + " cannot be created.",
                    RequirementSeverity.ERROR);
        }
        return Files.isWritable(path) ? Requirement.of(title, "Writable", path.toString(), RequirementSeverity.OK)
                : Requirement.of(title, "Not writable", "The site cannot write to " + path + ".",
                        RequirementSeverity.ERROR);
    }

    private Requirement mail() {
        String host = environment.getProperty("spring.mail.host", "");
        if (host.isBlank()) {
            return Requirement.of("Mail server", "Not configured",
                    "Set spring.mail.host so the site can send account and contact mail.",
                    RequirementSeverity.WARNING);
        }
        return Requirement.of("Mail server", host + ":" + environment.getProperty("spring.mail.port", "25"), "",
                RequirementSeverity.OK);
    }

    private Requirement trustedHosts() {
        if (trustedHosts.patterns().isEmpty()) {
            return Requirement.of("Trusted host patterns", "Not set", "The site answers to any host name. Set "
                    + "springdrop.security.trusted-host.patterns to the names it is reached by.",
                    RequirementSeverity.WARNING);
        }
        return Requirement.of("Trusted host patterns", String.join(", ", trustedHosts.patterns()), "",
                RequirementSeverity.OK);
    }

    private Requirement registration() {
        UserSettings settings = config.read(UserSettings.CONFIG_NAME, UserSettings.class, UserSettings.DEFAULTS);
        if (UserSettings.OPEN.equals(settings.registrationMode()) && !settings.requireEmailVerification()) {
            return Requirement.of("Account registration", "Open without email verification", "Anyone can create "
                    + "an account and use it at once, without proving the address is theirs.",
                    RequirementSeverity.WARNING);
        }
        return Requirement.of("Account registration", settings.registrationMode(), "", RequirementSeverity.OK);
    }

    /** A length of time in its largest whole unit, such as "3 days" or "1 hour". */
    static String ago(Duration age) {
        if (age.toDays() > 0) {
            return plural(age.toDays(), "day");
        }
        if (age.toHours() > 0) {
            return plural(age.toHours(), "hour");
        }
        return plural(age.toMinutes(), "minute");
    }

    private static String plural(long count, String unit) {
        return count + " " + unit + (count == 1 ? "" : "s");
    }
}
