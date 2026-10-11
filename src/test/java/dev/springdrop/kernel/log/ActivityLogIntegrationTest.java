package dev.springdrop.kernel.log;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
class ActivityLogIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-05-04T10:15:00Z");

    @Autowired
    private DatabaseLog database;

    @Autowired
    private ActivityLog siteLog;

    @Autowired
    private ConfigStore config;

    private final List<LogEntry> collected = new ArrayList<>();

    private final LogBackend collecting = new LogBackend() {
        @Override
        public String id() {
            return "collecting";
        }

        @Override
        public void write(LogEntry entry) {
            collected.add(entry);
        }
    };

    private final LogBackend failing = new LogBackend() {
        @Override
        public String id() {
            return "failing";
        }

        @Override
        public void write(LogEntry entry) {
            throw new IllegalStateException("disk full");
        }
    };

    @BeforeEach
    void emptyLog() {
        database.clear();
    }

    @AfterEach
    void signOutAndRestoreSettings() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        config.delete(LoggingSettings.CONFIG_NAME);
    }

    /** A log writing to the backends given, each of them on in the settings. */
    private ActivityLog log(LogBackend... backends) {
        ActivityLog log = new ActivityLog(config, List.of(backends), Clock.fixed(NOW, ZoneOffset.UTC));
        log.save(new LoggingSettings(log.backendIds()));
        return log;
    }

    @Test
    void anEventLoggedOutsideARequestIsStoredWithoutAccountOrAddress() {
        // The test framework stands up a request for each test; cron and other background work have none.
        RequestContextHolder.resetRequestAttributes();

        siteLog.channel("cron").notice("Cron ran {count} jobs.", Map.of("count", "3"));

        LogEntry entry = database.page(LogFilter.ALL, 0, 10).getFirst();
        assertThat(entry.type()).isEqualTo("cron");
        assertThat(entry.severity()).isEqualTo(Severity.NOTICE);
        assertThat(entry.formatted()).isEqualTo("Cron ran 3 jobs.");
        assertThat(entry.uid()).isZero();
        assertThat(entry.location()).isEmpty();
        assertThat(entry.referer()).isEmpty();
        assertThat(entry.hostname()).isEmpty();
        assertThat(database.find(entry.id())).contains(entry);
    }

    @Test
    void anEventLoggedDuringARequestCarriesTheAccountAndTheAddresses() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/content");
        request.addHeader("Referer", "http://localhost/admin");
        request.setRemoteAddr("10.1.2.3");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        AccountPrincipal editor = new AccountPrincipal(7L, "editor", "", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(editor, null, editor.getAuthorities()));

        log(collecting).channel("content").info("Saved {title}.", Map.of("title", "Spring"));

        assertThat(collected).singleElement().satisfies(entry -> {
            assertThat(entry.uid()).isEqualTo(7L);
            assertThat(entry.location()).isEqualTo("http://localhost/admin/content");
            assertThat(entry.referer()).isEqualTo("http://localhost/admin");
            assertThat(entry.hostname()).isEqualTo("10.1.2.3");
            assertThat(entry.timestamp()).isEqualTo(NOW);
        });
    }

    @Test
    void aRequestWithoutARefererOrAnAccountLogsThemAsNone() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("visitor", null, List.of()));

        log(collecting).channel("content").warning("Odd.", Map.of());

        assertThat(collected.getFirst().referer()).isEmpty();
        assertThat(collected.getFirst().uid()).isZero();
    }

    @Test
    void eachChannelMethodLogsAtItsSeverity() {
        LogChannel channel = log(collecting).channel("php");

        channel.error("e", Map.of());
        channel.warning("w", Map.of());
        channel.notice("n", Map.of());
        channel.info("i", Map.of());

        assertThat(collected).extracting(LogEntry::severity)
                .containsExactly(Severity.ERROR, Severity.WARNING, Severity.NOTICE, Severity.INFO);
    }

    @Test
    void onlyTheBackendsTheSettingsNameGetTheEvent() {
        ActivityLog log = log(collecting, database);
        log.save(new LoggingSettings(List.of("collecting")));

        log.channel("user").notice("Signed in.", Map.of());

        assertThat(collected).hasSize(1);
        assertThat(database.count(LogFilter.ALL)).isZero();
        assertThat(log.backendIds()).containsExactly("collecting", DatabaseLog.ID);
        assertThat(siteLog.settings()).isEqualTo(new LoggingSettings(List.of("collecting")));
    }

    @Test
    void aFailingBackendDoesNotKeepTheEventFromTheOthers() {
        ActivityLog log = log(failing, collecting);

        log.channel("user").error("Locked out.", Map.of());

        assertThat(collected).hasSize(1);
    }

    @Test
    void theDatabaseLogFiltersByTypeAndSeverityNewestFirst() {
        siteLog.log("action", Severity.NOTICE, "first", Map.of());
        siteLog.log("action", Severity.ERROR, "second", Map.of());
        siteLog.log("user", Severity.NOTICE, "third", Map.of());

        assertThat(database.page(LogFilter.ALL, 0, 10)).extracting(LogEntry::message)
                .containsExactly("third", "second", "first");
        assertThat(database.page(new LogFilter(Optional.of("action"), Optional.empty()), 0, 10))
                .extracting(LogEntry::message).containsExactly("second", "first");
        assertThat(database.page(new LogFilter(Optional.of("action"), Optional.of(Severity.NOTICE)), 0, 10))
                .extracting(LogEntry::message).containsExactly("first");
        assertThat(database.page(LogFilter.ALL, 1, 1)).extracting(LogEntry::message).containsExactly("second");
        assertThat(database.count(new LogFilter(Optional.empty(), Optional.of(Severity.NOTICE)))).isEqualTo(2);
        assertThat(database.types()).containsExactly("action", "user");
    }

    @Test
    void clearingTheDatabaseLogRemovesEveryEvent() {
        siteLog.log("action", Severity.NOTICE, "first", Map.of());

        database.clear();

        assertThat(database.count(LogFilter.ALL)).isZero();
        assertThat(database.find(1L)).isEmpty();
    }
}
