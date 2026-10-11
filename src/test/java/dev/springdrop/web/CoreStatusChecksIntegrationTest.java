package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.file.FileSystemProperties;
import dev.springdrop.kernel.security.TrustedHostProperties;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.kernel.status.Requirement;
import dev.springdrop.kernel.status.RequirementSeverity;
import dev.springdrop.kernel.user.UserSettings;
import dev.springdrop.support.AbstractIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.env.MockEnvironment;

@SpringBootTest
class CoreStatusChecksIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-10T12:00:00Z");

    @Autowired
    private DSLContext dsl;

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CronRunner cron;

    @Autowired
    private CacheManager caches;

    @Autowired
    private FileSystemProperties files;

    @Autowired
    private ConfigStore config;

    @Autowired
    private StateService state;

    private DSLContext database;
    private Flyway migrations;
    private FileSystemProperties directories;
    private MockEnvironment environment;
    private TrustedHostProperties trustedHosts;

    @BeforeEach
    void theSiteAsTestsRunIt() {
        state.remove("system", "cron_last");
        database = dsl;
        migrations = flyway;
        directories = files;
        environment = new MockEnvironment();
        trustedHosts = new TrustedHostProperties(List.of());
    }

    @AfterEach
    void cleanUp() {
        state.remove("system", "cron_last");
        config.delete(UserSettings.CONFIG_NAME);
    }

    private Requirement line(String title) {
        return new CoreStatusChecks(database, migrations, cron, caches, directories, environment, trustedHosts,
                config, Clock.fixed(NOW, ZoneOffset.UTC)).requirements().stream()
                .filter(requirement -> requirement.title().equals(title)).findFirst().orElseThrow();
    }

    private void cronLastRan(Duration ago) {
        state.set("system", "cron_last", NOW.minus(ago).getEpochSecond());
    }

    private Flyway withPending(String location) {
        return Flyway.configure().dataSource(dataSource)
                .locations("classpath:db/migration/core", "classpath:db/" + location).load();
    }

    @Test
    void theDatabaseIsNamedByItsVersion() {
        assertThat(line("Database").value()).startsWith("PostgreSQL ");
        assertThat(line("Database").severity()).isEqualTo(RequirementSeverity.OK);
    }

    @Test
    void anUnreachableDatabaseIsAnError() {
        database = mock(DSLContext.class);
        when(database.fetchValue("select version()")).thenThrow(new DataAccessResourceFailureException("refused"));

        assertThat(line("Database").severity()).isEqualTo(RequirementSeverity.ERROR);
        assertThat(line("Database").description()).endsWith("refused");
    }

    @Test
    void aOneWordVersionIsShownWhole() {
        database = mock(DSLContext.class);
        when(database.fetchValue("select version()")).thenReturn("PostgreSQL");

        assertThat(line("Database").value()).isEqualTo("PostgreSQL");
    }

    @Test
    void pendingDatabaseUpdatesAreAnError() {
        assertThat(line("Database updates").severity()).isEqualTo(RequirementSeverity.OK);

        migrations = withPending("status-pending-one");
        assertThat(line("Database updates").severity()).isEqualTo(RequirementSeverity.ERROR);
        assertThat(line("Database updates").description()).isEqualTo("1 database update has not been applied.");

        migrations = withPending("status-pending-two");
        assertThat(line("Database updates").description()).isEqualTo("2 database updates have not been applied.");
    }

    @Test
    void cronIsAnErrorUntilItRunsThenWarnsWhenOverdue() {
        assertThat(line("Cron maintenance tasks").severity()).isEqualTo(RequirementSeverity.ERROR);
        assertThat(line("Cron maintenance tasks").linkPath()).isEqualTo(CronController.PATH);

        cronLastRan(Duration.ofMinutes(1));
        assertThat(line("Cron maintenance tasks").severity()).isEqualTo(RequirementSeverity.OK);
        assertThat(line("Cron maintenance tasks").value()).isEqualTo("Last run 1 minute ago");

        cronLastRan(Duration.ofDays(3));
        assertThat(line("Cron maintenance tasks").severity()).isEqualTo(RequirementSeverity.WARNING);
        assertThat(line("Cron maintenance tasks").description()).isEqualTo("Cron has not run for 3 days.");

        cronLastRan(Duration.ofDays(15));
        assertThat(line("Cron maintenance tasks").severity()).isEqualTo(RequirementSeverity.ERROR);
    }

    @Test
    void anAgeIsGivenInItsLargestWholeUnit() {
        assertThat(CoreStatusChecks.ago(Duration.ofMinutes(5))).isEqualTo("5 minutes");
        assertThat(CoreStatusChecks.ago(Duration.ofMinutes(61))).isEqualTo("1 hour");
        assertThat(CoreStatusChecks.ago(Duration.ofHours(5))).isEqualTo("5 hours");
        assertThat(CoreStatusChecks.ago(Duration.ofHours(25))).isEqualTo("1 day");
    }

    @Test
    void theCacheBackendIsReported() {
        assertThat(line("Cache backend").value()).isEqualTo(caches.getClass().getSimpleName());
        assertThat(line("Cache backend").severity()).isEqualTo(RequirementSeverity.INFO);
    }

    @Test
    void theFileDirectoriesAreWritable() {
        assertThat(line("Public files directory").severity()).isEqualTo(RequirementSeverity.OK);
        assertThat(line("Private files directory").severity()).isEqualTo(RequirementSeverity.OK);
        assertThat(line("Temporary directory").severity()).isEqualTo(RequirementSeverity.OK);
    }

    @Test
    void aDirectoryThatCannotBeCreatedOrWrittenIsAnError() throws IOException {
        Path blocker = Files.createTempFile(FILES, "blocker", ".txt");
        Path readOnly = Files.createTempDirectory(FILES, "read-only");
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-xr-xr-x"));
        directories = new FileSystemProperties(blocker.resolve("public").toString(), readOnly.toString());
        try {
            assertThat(line("Public files directory").description()).endsWith("cannot be created.");
            assertThat(line("Private files directory").value()).isEqualTo("Not writable");
            assertThat(line("Private files directory").severity()).isEqualTo(RequirementSeverity.ERROR);
        } finally {
            Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
    }

    @Test
    void aSiteWithoutAMailServerIsWarned() {
        assertThat(line("Mail server").severity()).isEqualTo(RequirementSeverity.WARNING);

        environment.setProperty("spring.mail.host", "mail.example.com");
        assertThat(line("Mail server").value()).isEqualTo("mail.example.com:25");
        assertThat(line("Mail server").severity()).isEqualTo(RequirementSeverity.OK);
    }

    @Test
    void aSiteAnsweringToAnyHostIsWarned() {
        assertThat(line("Trusted host patterns").severity()).isEqualTo(RequirementSeverity.WARNING);

        trustedHosts = new TrustedHostProperties(List.of("^example\\.com$"));
        assertThat(line("Trusted host patterns").severity()).isEqualTo(RequirementSeverity.OK);
    }

    @Test
    void openRegistrationWithoutVerificationIsWarned() {
        assertThat(line("Account registration").severity()).isEqualTo(RequirementSeverity.OK);

        config.save(UserSettings.CONFIG_NAME, new UserSettings(UserSettings.OPEN, true));
        assertThat(line("Account registration").severity()).isEqualTo(RequirementSeverity.OK);

        config.save(UserSettings.CONFIG_NAME, new UserSettings(UserSettings.OPEN, false));
        assertThat(line("Account registration").severity()).isEqualTo(RequirementSeverity.WARNING);
    }
}
