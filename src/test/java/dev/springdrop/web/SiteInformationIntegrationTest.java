package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.datetime.SiteDateSettings;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.language.LanguageSettings;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.web.ErrorPagesConfig;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.ZoneId;
import java.util.LinkedHashMap;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SiteInformationIntegrationTest extends AbstractIntegrationTest {

    private static final List<String> SETTINGS = List.of(SiteInformation.CONFIG_NAME, ErrorPagesConfig.CONFIG_NAME,
            SiteDateSettings.CONFIG_NAME, LanguageSettings.CONFIG_NAME);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigStore config;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private DateFormatService dates;

    private final Map<String, ConfigStore.StoredConfig> saved = new LinkedHashMap<>();

    @BeforeEach
    void rememberTheSettings() {
        SETTINGS.forEach(name -> {
            ConfigStore.StoredConfig stored = config.everything().get(name);
            if (stored != null) {
                saved.put(name, stored);
            }
        });
        types.save(NodeType.of("page", "Basic page"));
        languages.install();
    }

    @AfterEach
    void restoreTheSettings() {
        SETTINGS.forEach(name -> {
            ConfigStore.StoredConfig stored = saved.get(name);
            if (stored == null) {
                config.delete(name);
            } else {
                config.saveImported(name, stored.data(), stored.uuid());
            }
        });
        types.delete("page");
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder submit(Map<String, String> changes) {
        Map<String, String> values = new LinkedHashMap<>(Map.of(
                SiteInformationController.NAME, "Bakery News",
                SiteInformationController.SLOGAN, "Fresh every morning",
                SiteInformationController.MAIL, "news@bakery.example",
                SiteInformationController.FRONT_PAGE, "",
                SiteInformationController.FORBIDDEN_PAGE, "",
                SiteInformationController.NOT_FOUND_PAGE, "",
                SiteInformationController.TIMEZONE, "America/Chicago",
                SiteInformationController.LANGUAGE, "en"));
        values.putAll(changes);
        MockHttpServletRequestBuilder request = post(SiteInformationController.PATH);
        values.forEach((name, value) -> request.param(name, value));
        return request;
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(send(request).getContentAsString());
    }

    @Test
    void changingTheSiteNameAndFrontPageIsReflectedOnTheRenderedSite() throws Exception {
        long hours = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, "page", "Opening hours",
                Map.of(BaseFieldDefinition.STATUS, true)), 1L).id()).longValue();
        try {
            MockHttpServletResponse saved = send(submit(Map.of(SiteInformationController.FRONT_PAGE,
                    NodeEntityType.path(hours))));
            assertThat(saved.getRedirectedUrl()).isEqualTo(SiteInformationController.PATH);

            MockHttpServletResponse home = mockMvc.perform(get("/")).andReturn().getResponse();
            assertThat(home.getForwardedUrl()).isEqualTo(NodeEntityType.path(hours));

            MockHttpServletResponse node = mockMvc.perform(get(home.getForwardedUrl()).with(user(
                    new AccountPrincipal(7L, "reader", "", true, List.of(NodePermissions.ACCESS_CONTENT)))))
                    .andReturn().getResponse();
            assertThat(node.getStatus()).isEqualTo(200);
            Document shown = Jsoup.parse(node.getContentAsString());
            assertThat(shown.text()).contains("Bakery News", "Opening hours");
        } finally {
            nodes.delete(hours);
        }
    }

    @Test
    void theFormShowsTheCurrentSettingsAndSavesEachOfThem() throws Exception {
        languages.save(new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr", ""));
        try {
            send(submit(Map.of(SiteInformationController.FORBIDDEN_PAGE, "/denied",
                    SiteInformationController.NOT_FOUND_PAGE, "/missing", SiteInformationController.LANGUAGE, "fr")));

            Document form = page(get(SiteInformationController.PATH));
            assertThat(form.selectFirst("input[name=site_name]").val()).isEqualTo("Bakery News");
            assertThat(form.selectFirst("input[name=site_slogan]").val()).isEqualTo("Fresh every morning");
            assertThat(form.selectFirst("input[name=front_page]").val()).isEqualTo(SiteInformation.DEFAULT_FRONT_PAGE);
            assertThat(form.selectFirst("select[name=timezone] option[selected]").val()).isEqualTo("America/Chicago");
            assertThat(config.read(ErrorPagesConfig.CONFIG_NAME, ErrorPagesConfig.class, null))
                    .isEqualTo(new ErrorPagesConfig("/denied", "/missing"));
            assertThat(dates.siteTimezone()).isEqualTo(ZoneId.of("America/Chicago"));
            assertThat(languages.defaultLangcode()).isEqualTo("fr");
        } finally {
            languages.makeDefault("en");
            languages.delete("fr");
        }
    }

    @Test
    void eachFieldIsCheckedAndNothingIsSavedWhenOneFails() throws Exception {
        SiteInformation before = config.read(SiteInformation.CONFIG_NAME, SiteInformation.class,
                SiteInformation.DEFAULTS);

        Document refused = page(submit(Map.of(SiteInformationController.NAME, "",
                SiteInformationController.MAIL, "not an address",
                SiteInformationController.FRONT_PAGE, "node/1",
                SiteInformationController.NOT_FOUND_PAGE, "missing",
                SiteInformationController.TIMEZONE, "Mars/Olympus_Mons",
                SiteInformationController.LANGUAGE, "xx")));

        assertThat(refused.select(".invalid-feedback")).hasSizeGreaterThanOrEqualTo(6);
        assertThat(refused.text()).contains("Enter a path starting with /.", "Choose a timezone.",
                "Choose a language.");
        assertThat(refused.selectFirst(".alert-success")).isNull();
        assertThat(config.read(SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS))
                .isEqualTo(before);
    }

    @Test
    void theFormNeedsTheSiteConfigurationPermission() throws Exception {
        assertThat(mockMvc.perform(get(SiteInformationController.PATH).with(user("editor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
