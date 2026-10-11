package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.language.ConfigTranslation;
import dev.springdrop.kernel.language.ConfigTranslationManager;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.locale.Translator;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigTranslationIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUMMARY = "summary";

    private static final String INSTANCE = FieldInstanceConfig.configName(NodeEntityType.ID, ARTICLE, SUMMARY);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private ConfigTranslationManager translations;

    @Autowired
    private ConfigStore config;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private Translator translator;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private long node;

    @BeforeEach
    void anArticleWithASummaryOnAFrenchSite() {
        clear();
        menus.install();
        storedLinks.install();
        languages.save(new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr", ""));
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(FieldStorageConfig.single(SUMMARY, NodeEntityType.ID, StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        node = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule",
                Map.of(SUMMARY, "Opening hours", "status", true, "owner", 1L)), 1L).id()).longValue();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        config.listNames(ConfigTranslation.CONFIG_PREFIX).forEach(config::delete);
        config.delete(SiteInformation.CONFIG_NAME);
        dsl.deleteFrom(table("locale_source")).execute();
        translator.deleteTranslations("fr");
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.findStorage(NodeEntityType.ID, SUMMARY)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, SUMMARY));
        types.all().forEach(type -> types.delete(type.id()));
        languages.delete("fr");
    }

    private static RequestPostProcessor translatorAccount() {
        return user("translator").authorities(new SimpleGrantedAuthority(Permissions.TRANSLATE_CONFIGURATION),
                new SimpleGrantedAuthority(Permissions.ADMINISTER_FIELDS),
                new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(translatorAccount())).andReturn().getResponse()
                .getContentAsString());
    }

    private void translateSummaryLabel(String label) throws Exception {
        mockMvc.perform(post(ConfigTranslationController.translatePath(INSTANCE, "fr"))
                .param("translation_0", label).param("translation_1", "")
                .with(csrf()).with(translatorAccount()));
    }

    @Test
    void aTranslatedFieldLabelRendersInTheActiveLanguageWhileTheSourceIsUnchanged() throws Exception {
        assertThat(page(NodeEntityType.path(node)).text()).contains("Summary");

        translateSummaryLabel("Résumé");

        assertThat(page("/fr" + NodeEntityType.path(node)).text()).contains("Résumé").doesNotContain("Summary");
        assertThat(page(NodeEntityType.path(node)).text()).contains("Summary").doesNotContain("Résumé");
        assertThat(fields.findInstance(NodeEntityType.ID, ARTICLE, SUMMARY).orElseThrow().label())
                .isEqualTo("Summary");
        assertThat(config.readStored(INSTANCE).orElseThrow().get("label").asString()).isEqualTo("Summary");
    }

    @Test
    void anAdministrationPageShowsTheSourceInAnyLanguage() throws Exception {
        translateSummaryLabel("Résumé");

        Document edit = page("/fr" + FieldUiController.fieldsPath(NodeEntityType.ID, ARTICLE) + "/" + SUMMARY);

        assertThat(edit.selectFirst("input[name=label]").val()).isEqualTo("Summary");
    }

    @Test
    void theListingOffersEveryTranslatableObjectAndEachOneItsLanguages() throws Exception {
        config.save(SiteInformation.CONFIG_NAME, SiteInformation.DEFAULTS);
        Document listing = page(ConfigTranslationController.PATH);

        assertThat(listing.select("tr[data-config]").eachAttr("data-config")).contains(INSTANCE,
                ViewConfig.configName(DefaultViews.CONTENT), SiteInformation.CONFIG_NAME);
        assertThat(listing.selectFirst("tr[data-config=\"" + INSTANCE + "\"]").text())
                .contains("Field", "Summary");
        BootstrapAssertions.assertNoOutlineButtons(listing);

        translateSummaryLabel("Résumé");
        Document item = page(ConfigTranslationController.itemPath(INSTANCE));
        assertThat(item.selectFirst("tr[data-language=fr]").text()).contains("Translated", "Edit");

        Document form = page(ConfigTranslationController.translatePath(INSTANCE, "fr"));
        assertThat(form.select(".config-source").eachText()).containsExactly("Summary");
        assertThat(form.selectFirst("textarea[name=translation_0]").text()).isEqualTo("Résumé");
    }

    @Test
    void emptyingEveryBoxRemovesTheTranslation() throws Exception {
        translateSummaryLabel("Résumé");
        translateSummaryLabel("");

        assertThat(translations.translation("fr", INSTANCE)).isEmpty();
        assertThat(page(ConfigTranslationController.itemPath(INSTANCE)).selectFirst("tr[data-language=fr]").text())
                .contains("Not translated", "Add");
    }

    @Test
    void theSiteNameIsTranslated() throws Exception {
        config.save(SiteInformation.CONFIG_NAME, new SiteInformation("City library", "", "admin@example.com",
                "http://localhost"));
        translations.save("fr", SiteInformation.CONFIG_NAME, Map.of("name", "Bibliothèque municipale"));

        assertThat(page("/fr" + NodeEntityType.path(node)).title()).endsWith("Bibliothèque municipale");
        assertThat(page(NodeEntityType.path(node)).title()).endsWith("City library");
    }

    @Test
    void aMenuLinkDeclaredInCodeIsShownInTheActiveLanguage() throws Exception {
        translator.save("fr", "Home", "", "Accueil");

        assertThat(page("/fr" + NodeEntityType.path(node)).select("nav a").eachText()).contains("Accueil");
        assertThat(page(NodeEntityType.path(node)).select("nav a").eachText()).contains("Home")
                .doesNotContain("Accueil");
    }

    @Test
    void unknownObjectsAndLanguagesAreNotFound() throws Exception {
        assertThat(mockMvc.perform(get(ConfigTranslationController.itemPath("image.style.thumbnail"))
                .with(translatorAccount())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(ConfigTranslationController.translatePath(INSTANCE, "en"))
                .with(translatorAccount())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(post(ConfigTranslationController.translatePath(INSTANCE, "de")).with(csrf())
                .with(translatorAccount())).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void aSiteWithEnglishAloneHasNoLanguageToTranslateInto() throws Exception {
        languages.delete("fr");

        assertThat(page(ConfigTranslationController.itemPath(INSTANCE)).selectFirst(".no-languages")).isNotNull();
    }

    @Test
    void translatingConfigurationIsClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(ConfigTranslationController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
