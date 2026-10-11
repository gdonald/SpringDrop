package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.cron.AutomatedCron;
import dev.springdrop.kernel.cron.AutomatedCronSettings;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
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
class PerformanceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AutomatedCron automatedCron;

    @Autowired
    private ConfigStore config;

    @AfterEach
    void turnAutomatedCronOff() {
        config.delete(AutomatedCronSettings.CONFIG_NAME);
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private MockHttpServletResponse choose(String interval) throws Exception {
        return mockMvc.perform(post(PerformanceController.PATH)
                .param(PerformanceController.AUTOMATED_CRON_INTERVAL, interval)
                .with(csrf()).with(administrator())).andReturn().getResponse();
    }

    @Test
    void theFormOffersEachIntervalWithNeverChosenByDefault() throws Exception {
        Document form = Jsoup.parse(mockMvc.perform(get(PerformanceController.PATH).with(administrator()))
                .andReturn().getResponse().getContentAsString());

        assertThat(form.select("select[name=automated_cron_interval] option").eachAttr("value"))
                .containsExactly("0", "3600", "10800", "21600", "43200", "86400", "604800");
        assertThat(form.selectFirst("select[name=automated_cron_interval] option[selected]").text())
                .isEqualTo("Never");
        assertThat(form.selectFirst("select[name=automated_cron_interval]").hasAttr("required")).isTrue();
    }

    @Test
    void savingAnIntervalTurnsAutomatedCronOn() throws Exception {
        assertThat(choose("10800").getRedirectedUrl()).isEqualTo(PerformanceController.PATH);

        assertThat(automatedCron.settings()).isEqualTo(new AutomatedCronSettings(10800));
    }

    @Test
    void anIntervalTheFormDoesNotOfferOrNoIntervalIsRefused() throws Exception {
        assertThat(choose("5").getContentAsString()).contains("Choose an interval.");
        assertThat(choose("").getContentAsString()).contains("This value is required.");

        assertThat(automatedCron.settings()).isEqualTo(AutomatedCronSettings.DEFAULTS);
    }

    @Test
    void theSettingsAreClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(PerformanceController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
