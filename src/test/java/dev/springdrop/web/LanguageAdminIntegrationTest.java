package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.language.LanguageNegotiator;
import dev.springdrop.kernel.language.LanguageSettings;
import dev.springdrop.kernel.language.NegotiationSettings;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
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
class LanguageAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String CHECKED = FormRenderer.CHECKED_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private LanguageNegotiator negotiator;

    @Autowired
    private ConfigStore config;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @BeforeEach
    void englishAlone() {
        accounts.install();
        clear();
    }

    @AfterEach
    void englishAloneAgain() {
        clear();
    }

    private void clear() {
        config.delete(LanguageSettings.CONFIG_NAME);
        config.delete(NegotiationSettings.CONFIG_NAME);
        languages.all().stream().map(LanguageConfig::id).filter(id -> !id.equals(LanguageManager.ENGLISH))
                .forEach(languages::delete);
        accounts.findByName("ada").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_LANGUAGES),
                new SimpleGrantedAuthority(Permissions.ADMINISTER_USERS));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(administrator())).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletResponse submit(String path, String... fields) throws Exception {
        MockHttpServletRequestBuilder request = post(path);
        for (int index = 0; index < fields.length; index += 2) {
            request.param(fields[index], fields[index + 1]);
        }
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private static List<String> errors(MockHttpServletResponse response) throws Exception {
        return Jsoup.parse(response.getContentAsString()).select(".invalid-feedback").eachText();
    }

    private MockHttpServletResponse addFrench(String... more) throws Exception {
        String[] fields = {LanguageController.LANGCODE, "fr", LanguageController.LABEL, "French",
                LanguageController.DIRECTION, LanguageConfig.LTR, LanguageController.WEIGHT, "1",
                LanguageController.PREFIX, "fr", LanguageController.DOMAIN, "fr.example.com"};
        String[] all = new String[fields.length + more.length];
        System.arraycopy(fields, 0, all, 0, fields.length);
        System.arraycopy(more, 0, all, fields.length, more.length);
        return submit(LanguageController.ADD_PATH, all);
    }

    @Test
    void theListingShowsEachLanguageWithTheDefaultMarkedAndNotDeletable() throws Exception {
        addFrench();

        Document listing = page(LanguageController.PATH);

        assertThat(listing.select("tr[data-language]").eachAttr("data-language")).containsExactly("en", "fr");
        assertThat(listing.selectFirst("tr[data-language=en]").text()).contains("Default");
        assertThat(listing.select("tr[data-language=en] a").eachText()).containsExactly("Edit");
        assertThat(listing.select("tr[data-language=fr] a").eachText()).containsExactly("Edit", "Delete");
        BootstrapAssertions.assertNoOutlineButtons(listing);
        BootstrapAssertions.assertEditControlsAreButtons(listing);
    }

    @Test
    void addingALanguageStoresItAndCanMakeItTheDefault() throws Exception {
        assertThat(page(LanguageController.ADD_PATH).selectFirst("input[name=langcode]").hasAttr("required"))
                .isTrue();

        assertThat(addFrench(LanguageController.DEFAULT, CHECKED).getRedirectedUrl())
                .isEqualTo(LanguageController.PATH);

        assertThat(languages.find("fr")).contains(new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr",
                "fr.example.com"));
        assertThat(languages.defaultLangcode()).isEqualTo("fr");
    }

    @Test
    void aTakenCodePrefixOrDomainOrAWrongDirectionIsRefused() throws Exception {
        addFrench();

        assertThat(errors(addFrench())).containsExactly("The language fr exists already.");
        assertThat(errors(submit(LanguageController.ADD_PATH, LanguageController.LANGCODE, "ca",
                LanguageController.LABEL, "Catalan", LanguageController.DIRECTION, LanguageConfig.LTR,
                LanguageController.PREFIX, "fr", LanguageController.DOMAIN, "fr.example.com")))
                .containsExactly("Another language uses the prefix fr.",
                        "Another language uses the domain fr.example.com.");
        assertThat(errors(submit(LanguageController.ADD_PATH, LanguageController.LANGCODE, "French",
                LanguageController.LABEL, "", LanguageController.DIRECTION, "up", LanguageController.PREFIX, "F R",
                LanguageController.DOMAIN, "not a host", LanguageController.WEIGHT, "x")))
                .contains("Enter a language code such as fr or pt-br.", "This value is required.",
                        "Choose a direction.", "Use lowercase letters, digits, and hyphens.", "Enter a host name.",
                        "The weight is a whole number.");
    }

    @Test
    void aRightToLeftLanguageWithoutPrefixOrDomainIsAdded() throws Exception {
        submit(LanguageController.ADD_PATH, LanguageController.LANGCODE, "ar", LanguageController.LABEL, "Arabic",
                LanguageController.DIRECTION, LanguageConfig.RTL, LanguageController.PREFIX, "",
                LanguageController.DOMAIN, "");

        assertThat(languages.find("ar")).contains(new LanguageConfig("ar", "Arabic", LanguageConfig.RTL, 0, "", ""));
        assertThat(errors(submit(LanguageController.ADD_PATH, LanguageController.LANGCODE, "ca",
                LanguageController.LABEL, "Catalan", LanguageController.DIRECTION, "")))
                .containsExactly("This value is required.");
    }

    @Test
    void aLanguageIsEditedWithItsCodeFixed() throws Exception {
        addFrench();

        Document form = page(LanguageController.managePath("fr"));
        assertThat(form.select("input[name=langcode]")).isEmpty();
        assertThat(form.selectFirst("input[name=label]").val()).isEqualTo("French");

        submit(LanguageController.managePath("fr"), LanguageController.LABEL, "Français",
                LanguageController.DIRECTION, LanguageConfig.LTR, LanguageController.WEIGHT, "1",
                LanguageController.PREFIX, "fr", LanguageController.DOMAIN, "");

        assertThat(languages.find("fr").orElseThrow().label()).isEqualTo("Français");
        assertThat(page(LanguageController.managePath("en")).selectFirst("input[name=default]").hasAttr("checked"))
                .isTrue();
    }

    @Test
    void aLanguageIsDeletedAfterConfirmationButTheDefaultNeverIs() throws Exception {
        addFrench();

        assertThat(page(LanguageController.managePath("fr") + "/delete").title())
                .isEqualTo("Delete the language French?");
        submit(LanguageController.managePath("fr") + "/delete");

        assertThat(languages.find("fr")).isEmpty();
        assertThat(mockMvc.perform(get(LanguageController.managePath("en") + "/delete").with(administrator()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(LanguageController.managePath("xx")).with(administrator())).andReturn()
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void theDetectionSettingsTurnMethodsOnInTheOrderGiven() throws Exception {
        Document form = page(LanguageController.DETECTION_PATH);
        assertThat(form.selectFirst("input[name=url]").hasAttr("checked")).isTrue();
        assertThat(form.selectFirst("input[name=session]").hasAttr("checked")).isFalse();

        submit(LanguageController.DETECTION_PATH, "session", CHECKED, "weight_session", "-1", "url", CHECKED,
                "weight_url", "4", "weight_user", "2", LanguageController.URL_SOURCE, NegotiationSettings.DOMAIN);

        assertThat(negotiator.settings()).isEqualTo(new NegotiationSettings(
                List.of(NegotiationSettings.SESSION, NegotiationSettings.URL), NegotiationSettings.DOMAIN));
    }

    @Test
    void theUrlSourceIsRequiredAndThePrefixSourceIsAccepted() throws Exception {
        assertThat(errors(submit(LanguageController.DETECTION_PATH, "weight_url", "0", "weight_user", "1",
                "weight_session", "2", LanguageController.URL_SOURCE, ""))).containsExactly("This value is required.");

        submit(LanguageController.DETECTION_PATH, "user", CHECKED, "weight_url", "0", "weight_user", "1",
                "weight_session", "2", LanguageController.URL_SOURCE, NegotiationSettings.PREFIX);
        assertThat(negotiator.settings()).isEqualTo(new NegotiationSettings(List.of(NegotiationSettings.USER),
                NegotiationSettings.PREFIX));
    }

    @Test
    void aLanguageForAnAccountThatIsGoneChangesNothing() throws Exception {
        assertThat(submit(PeopleController.PATH + "/999999/edit", PeopleController.LANGUAGE, "en")
                .getRedirectedUrl()).isEqualTo(PeopleController.PATH);
    }

    @Test
    void aWrongOrderOrUrlSourceIsRefused() throws Exception {
        assertThat(errors(submit(LanguageController.DETECTION_PATH, "weight_url", "first", "weight_user", "1",
                "weight_session", "2", LanguageController.URL_SOURCE, "cookie")))
                .contains("The order is a whole number.", "Choose where the URL names the language.");
        assertThat(negotiator.settings()).isEqualTo(NegotiationSettings.DEFAULTS);
    }

    @Test
    void anAccountsPreferredLanguageIsSetOnItsEditForm() throws Exception {
        addFrench();
        long ada = accounts.create("ada", "ada@example.com", "").id();

        Document form = page(PeopleController.PATH + "/" + ada + "/edit");
        assertThat(form.select("select[name=preferred_language] option").eachAttr("value"))
                .containsExactly("en", "fr");

        submit(PeopleController.PATH + "/" + ada + "/edit", PeopleController.LANGUAGE, "fr");
        assertThat(accounts.find(ada).orElseThrow().preferredLanguage()).isEqualTo("fr");

        assertThat(errors(submit(PeopleController.PATH + "/" + ada + "/edit", PeopleController.LANGUAGE, "xx")))
                .containsExactly("Choose a language.");
        assertThat(accounts.find(ada).orElseThrow().preferredLanguage()).isEqualTo("fr");
    }

    @Test
    void theLanguagePagesAreClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(LanguageController.PATH).with(user("visitor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }
}
