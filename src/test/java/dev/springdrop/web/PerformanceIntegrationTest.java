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
import dev.springdrop.kernel.asset.Assets;
import dev.springdrop.kernel.cache.PageCache;
import dev.springdrop.kernel.cache.PerformanceSettings;
import dev.springdrop.kernel.render.CacheMetadata;
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

    @Autowired
    private PageCache pages;

    @Autowired
    private Assets assets;

    @AfterEach
    void turnAutomatedCronOff() {
        config.delete(AutomatedCronSettings.CONFIG_NAME);
        config.delete(PerformanceSettings.CONFIG_NAME);
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private MockHttpServletResponse choose(String interval) throws Exception {
        return save(interval, "600", false);
    }

    private MockHttpServletResponse save(String interval, String maxAge, boolean aggregate) throws Exception {
        var request = post(PerformanceController.PATH)
                .param(PerformanceController.AUTOMATED_CRON_INTERVAL, interval)
                .param(PerformanceController.PAGE_CACHE_MAX_AGE, maxAge);
        if (aggregate) {
            request.param(PerformanceController.AGGREGATE_JS, "true");
        }
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private String performancePage() throws Exception {
        return mockMvc.perform(get(PerformanceController.PATH).with(administrator())).andReturn().getResponse()
                .getContentAsString();
    }

    @Test
    void togglingAggregationChangesWhereScriptsLoadFrom() throws Exception {
        assertThat(save("0", "600", true).getRedirectedUrl()).isEqualTo(PerformanceController.PATH);
        String bundle = assets.url("/js/form-states.js");
        assertThat(bundle).startsWith(Assets.BUNDLE_PREFIX);
        String bundleFile = bundle.substring(bundle.lastIndexOf('/') + 1);
        assertThat(Jsoup.parse(performancePage()).select("script").html()).contains(bundleFile)
                .doesNotContain("form-states.js");
        assertThat(Jsoup.parse(performancePage()).selectFirst("input[name=preprocess_js]").hasAttr("checked"))
                .isTrue();

        save("0", "600", false);

        assertThat(Jsoup.parse(performancePage()).select("script").html()).contains("form-states.js")
                .doesNotContain(bundleFile);
    }

    @Test
    void thePageCacheKeepsPagesAsLongAsTheSettingsSay() throws Exception {
        save("0", "0", false);
        pages.keepFinished("/kept", CacheMetadata.EMPTY, new PageCache.Finished("<p>kept</p>", "text/html", "n"));
        assertThat(pages.finished("/kept")).isEmpty();

        save("0", "3600", false);
        pages.keepFinished("/kept", CacheMetadata.EMPTY, new PageCache.Finished("<p>kept</p>", "text/html", "n"));
        assertThat(pages.finished("/kept")).isPresent();
        assertThat(Jsoup.parse(performancePage()).selectFirst("select[name=page_cache_maximum_age] option[selected]")
                .text()).isEqualTo("1 hour");
    }

    @Test
    void clearingTheCachesEmptiesThem() throws Exception {
        save("0", "3600", false);
        pages.keepFinished("/kept", CacheMetadata.EMPTY, new PageCache.Finished("<p>kept</p>", "text/html", "n"));

        MockHttpServletResponse cleared = mockMvc.perform(post(PerformanceController.CLEAR_PATH).with(csrf())
                .with(administrator())).andReturn().getResponse();

        assertThat(cleared.getRedirectedUrl()).isEqualTo(PerformanceController.PATH);
        assertThat(pages.finished("/kept")).isEmpty();
    }

    @Test
    void aCacheAgeTheFormDoesNotOfferIsRefused() throws Exception {
        assertThat(save("0", "7", false).getContentAsString()).contains("Choose how long pages are kept.");
        assertThat(save("0", "", false).getContentAsString()).contains("This value is required.");
        assertThat(config.read(PerformanceSettings.CONFIG_NAME, PerformanceSettings.class, null)).isNull();
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
