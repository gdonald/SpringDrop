package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.lock.LockService;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class CronIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CronRunner cron;

    @Autowired
    private LockService locks;

    @Autowired
    private StateService state;

    @BeforeEach
    void cronHasNotRun() {
        state.remove("system", "cron_last");
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private int trigger(String key) throws Exception {
        return mockMvc.perform(get(CronController.TRIGGER_PATH + "/" + key)).andReturn().getResponse().getStatus();
    }

    @Test
    void theCronUrlWithTheSiteKeyRunsCron() throws Exception {
        assertThat(trigger(cron.key())).isEqualTo(204);

        assertThat(cron.lastRun()).isPresent();
    }

    @Test
    void theCronUrlWithAnotherKeyIsRefusedAndRunsNothing() throws Exception {
        assertThat(trigger("guessed")).isEqualTo(403);

        assertThat(cron.lastRun()).isEmpty();
    }

    @Test
    void theCronUrlIsLockedOutWhileARunHoldsTheLock() throws Exception {
        String owner = locks.acquire(CronRunner.LOCK_NAME, CronRunner.LOCK_LEASE).orElseThrow();
        try {
            assertThat(trigger(cron.key())).isEqualTo(409);
        } finally {
            locks.release(CronRunner.LOCK_NAME, owner);
        }

        assertThat(cron.lastRun()).isEmpty();
    }

    @Test
    void theCronPageShowsTheCronUrlAndTheJobsBeforeCronHasRun() throws Exception {
        Document page = Jsoup.parse(mockMvc.perform(get(CronController.PATH).with(administrator()))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst(".last-run").text()).isEqualTo("Cron has not run.");
        assertThat(page.selectFirst(".cron-url").text()).isEqualTo("http://localhost/cron/" + cron.key());
        assertThat(page.select(".cron-jobs li").eachText()).isEqualTo(cron.jobIds());
        BootstrapAssertions.assertNoOutlineButtons(page);
    }

    @Test
    void theRunButtonRunsCronAndThePageShowsWhenItRan() throws Exception {
        MockHttpServletResponse ran = mockMvc.perform(post(CronController.RUN_PATH).with(csrf())
                .with(administrator())).andReturn().getResponse();

        assertThat(ran.getRedirectedUrl()).isEqualTo(CronController.PATH);
        Document page = Jsoup.parse(mockMvc.perform(get(CronController.PATH).with(administrator()))
                .andReturn().getResponse().getContentAsString());
        assertThat(page.selectFirst(".last-run").text()).startsWith("Cron last ran ");
    }

    @Test
    void theCronPageAndRunButtonAreClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(CronController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post(CronController.RUN_PATH).with(csrf()).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(cron.lastRun()).isEmpty();
    }
}
