package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.log.ActivityLog;
import dev.springdrop.kernel.log.DatabaseLog;
import dev.springdrop.kernel.log.DatabaseLogTrimmer;
import dev.springdrop.kernel.log.ErrorDisplay;
import dev.springdrop.kernel.log.LogFilter;
import dev.springdrop.kernel.log.LoggingSettings;
import dev.springdrop.kernel.log.Severity;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class LoggingSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivityLog log;

    @Autowired
    private DatabaseLog database;

    @Autowired
    private DatabaseLogTrimmer trimmer;

    @Autowired
    private ConfigStore config;

    @BeforeEach
    @AfterEach
    void defaults() {
        config.delete(LoggingSettings.CONFIG_NAME);
        database.clear();
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private MockHttpServletResponse choose(String display, String rows) throws Exception {
        return send(post(LoggingSettingsController.PATH).param(LoggingSettingsController.ERROR_DISPLAY, display)
                .param(LoggingSettingsController.ROWS, rows)
                .param(LoggingSettingsController.BACKEND_PREFIX + DatabaseLog.ID, "true"));
    }

    /** The page an anonymous visitor gets for a request that failed with an exception. */
    private Document errorPage() throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/broken")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException("The menu tree is not built.")))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void theErrorDisplayLevelChangesWhatAnonymousUsersSeeOnErrors() throws Exception {
        assertThat(errorPage().selectFirst(".error-message")).isNull();

        assertThat(choose(ErrorDisplay.ERRORS.name(), "1000").getRedirectedUrl())
                .isEqualTo(LoggingSettingsController.PATH);
        Document errors = errorPage();
        assertThat(errors.selectFirst(".error-message").text())
                .isEqualTo("IllegalStateException: The menu tree is not built.");
        assertThat(errors.selectFirst(".error-trace")).isNull();

        choose(ErrorDisplay.ALL.name(), "1000");
        assertThat(errorPage().selectFirst(".error-trace").text()).contains("IllegalStateException",
                "LoggingSettingsIntegrationTest");

        choose(ErrorDisplay.NONE.name(), "1000");
        assertThat(errorPage().selectFirst(".error-message")).isNull();
    }

    @Test
    void anErrorPageWithoutAnExceptionShowsNoDetail() throws Exception {
        choose(ErrorDisplay.ALL.name(), "1000");

        Document page = Jsoup.parse(mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 503)).andReturn().getResponse().getContentAsString());

        assertThat(page.text()).contains("Something went wrong.");
        assertThat(page.selectFirst(".error-message")).isNull();
    }

    @Test
    void aPageNotFoundIsLeftToItsOwnPage() throws Exception {
        choose(ErrorDisplay.ALL.name(), "1000");

        Document page = Jsoup.parse(mockMvc.perform(get("/error").accept(MediaType.TEXT_HTML)
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)).andReturn().getResponse().getContentAsString());

        assertThat(page.text()).contains("404").doesNotContain("Something went wrong.");
    }

    @Test
    void cronKeepsOnlyTheNewestEventsTheSettingsAllow() throws Exception {
        for (int place = 1; place <= 5; place++) {
            log.log("content", Severity.NOTICE, "Saved article " + place, Map.of());
        }

        choose(ErrorDisplay.NONE.name(), "0");
        trimmer.run();
        assertThat(database.count(LogFilter.ALL)).isEqualTo(5);

        log.save(new LoggingSettings(List.of(DatabaseLog.ID), ErrorDisplay.NONE, 3));
        trimmer.run();

        assertThat(database.page(LogFilter.ALL, 0, 10)).extracting(entry -> entry.message())
                .containsExactly("Saved article 5", "Saved article 4", "Saved article 3");
        database.clear();
        trimmer.run();
        assertThat(database.count(LogFilter.ALL)).isZero();
    }

    @Test
    void theFormShowsTheSettingsAndRefusesChoicesItDoesNotOffer() throws Exception {
        log.save(new LoggingSettings(List.of(DatabaseLog.ID), ErrorDisplay.ERRORS, 10000));

        Document form = Jsoup.parse(send(get(LoggingSettingsController.PATH))
                .getContentAsString());
        assertThat(form.selectFirst("select[name=error_level] option[selected]").val()).isEqualTo("ERRORS");
        assertThat(form.selectFirst("select[name=row_limit] option[selected]").val()).isEqualTo("10000");
        assertThat(form.selectFirst("input[name=backend_database]").hasAttr("checked")).isTrue();

        String refused = send(post(LoggingSettingsController.PATH).param(LoggingSettingsController.ERROR_DISPLAY,
                "LOUD").param(LoggingSettingsController.ROWS, "7")).getContentAsString();
        assertThat(refused).contains("Choose how much error pages show.", "Choose how many events to keep.");
        assertThat(log.settings().errorDisplay()).isEqualTo(ErrorDisplay.ERRORS);
    }

    @Test
    void oldSettingsWithoutTheNewKeysReadWithTheirDefaults() {
        config.save(LoggingSettings.CONFIG_NAME, Map.of());

        assertThat(log.settings()).isEqualTo(new LoggingSettings(List.of(DatabaseLog.ID), ErrorDisplay.NONE, 0));
    }

    @Test
    void theFormNeedsTheSiteConfigurationPermission() throws Exception {
        assertThat(mockMvc.perform(get(LoggingSettingsController.PATH).with(user("editor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
