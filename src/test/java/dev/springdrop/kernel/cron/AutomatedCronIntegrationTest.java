package dev.springdrop.kernel.cron;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@SpringBootTest
class AutomatedCronIntegrationTest extends AbstractIntegrationTest {

    private static final AutomatedCronSettings HOURLY = new AutomatedCronSettings(3600);

    @Autowired
    private AutomatedCron automatedCron;

    @Autowired
    private CronRunner cron;

    @Autowired
    private ConfigStore config;

    @Autowired
    private StateService state;

    @BeforeEach
    void cronHasNotRun() {
        state.remove(CronRunner.STATE_COLLECTION, CronRunner.LAST_RUN_KEY);
    }

    @AfterEach
    void turnAutomatedCronOff() {
        config.delete(AutomatedCronSettings.CONFIG_NAME);
    }

    private void lastRanAgo(Duration ago) {
        state.set(CronRunner.STATE_COLLECTION, CronRunner.LAST_RUN_KEY,
                Instant.now().minus(ago).getEpochSecond());
    }

    @Test
    void automatedCronIsOffByDefault() {
        assertThat(automatedCron.settings()).isEqualTo(AutomatedCronSettings.DEFAULTS);
        assertThat(automatedCron.isDue()).isFalse();
    }

    @Test
    void cronThatNeverRanIsDueOnceAutomatedCronIsOn() {
        automatedCron.save(HOURLY);

        assertThat(automatedCron.isDue()).isTrue();
    }

    @Test
    void cronIsDueOnlyOnceTheIntervalHasPassed() {
        automatedCron.save(HOURLY);

        lastRanAgo(Duration.ofMinutes(10));
        assertThat(automatedCron.isDue()).isFalse();

        lastRanAgo(Duration.ofHours(2));
        assertThat(automatedCron.isDue()).isTrue();
    }

    @Test
    void aRequestStartsCronAfterItIsAnsweredWhenCronIsDue() throws Exception {
        automatedCron.save(HOURLY);
        List<String> order = new ArrayList<>();
        AutomatedCronFilter filter = new AutomatedCronFilter(automatedCron, cron, work -> {
            order.add("cron");
            work.run();
        });
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) {
                order.add("answered");
            }
        };

        filter.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), chain);

        assertThat(order).containsExactly("answered", "cron");
        assertThat(cron.lastRun()).isPresent();
    }

    @Test
    void aRequestStartsNothingWhenCronIsNotDue() throws Exception {
        List<Runnable> started = new ArrayList<>();
        AutomatedCronFilter filter = new AutomatedCronFilter(automatedCron, cron, started::add);

        filter.doFilter(new MockHttpServletRequest("GET", "/"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(started).isEmpty();
    }
}
