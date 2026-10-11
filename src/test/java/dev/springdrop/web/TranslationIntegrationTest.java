package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.locale.Translator;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jooq.DSLContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class TranslationIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "page";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private Translator translator;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    private long draft;

    @BeforeEach
    void frenchAndADraft() {
        clear();
        languages.save(new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr", ""));
        types.save(NodeType.of(PAGE, "Basic page"));
        draft = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, PAGE, "Spring schedule",
                Map.of(BaseFieldDefinition.STATUS, false, BaseFieldDefinition.OWNER, 1L)), 1L).id()).longValue();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        dsl.deleteFrom(table("locale_source")).execute();
        translator.deleteTranslations("fr");
        languages.delete("fr");
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
    }

    private static RequestPostProcessor translatorAccount() {
        return user("translator").authorities(new SimpleGrantedAuthority(Permissions.TRANSLATE_INTERFACE),
                new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT),
                new SimpleGrantedAuthority(NodePermissions.VIEW_ANY_UNPUBLISHED));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(translatorAccount())).andReturn().getResponse()
                .getContentAsString());
    }

    private String badge(String path) throws Exception {
        return page(path).selectFirst(".badge.text-bg-warning").text();
    }

    private Element row(Document translate, String source) {
        return translate.select("table.translate-strings tbody tr").stream()
                .filter(row -> row.selectFirst("td div").text().equals(source)).findFirst().orElseThrow();
    }

    @Test
    void aTranslatedStringRendersInTheActiveLanguageAndSurvivesExportAndImport() throws Exception {
        assertThat(badge("/fr" + NodeEntityType.path(draft))).isEqualTo("Unpublished");

        Document translate = page(TranslationController.PATH + "?langcode=fr&search=Unpublished");
        Element unpublished = row(translate, "Unpublished");
        String field = unpublished.selectFirst("textarea").attr("name");
        mockMvc.perform(post(TranslationController.PATH).param(TranslationController.LANGCODE, "fr")
                .param(TranslationController.IDS, unpublished.attr("data-string") + ",x")
                .param(field, "Non publié").with(csrf()).with(translatorAccount()));

        assertThat(badge("/fr" + NodeEntityType.path(draft))).isEqualTo("Non publié");
        assertThat(badge(NodeEntityType.path(draft))).isEqualTo("Unpublished");

        MockHttpServletResponse exported = mockMvc.perform(get(TranslationController.EXPORT_PATH + "/fr")
                .with(translatorAccount())).andReturn().getResponse();
        assertThat(exported.getHeader("Content-Disposition")).contains("springdrop.fr.po");
        String po = exported.getContentAsString(StandardCharsets.UTF_8);
        assertThat(po).contains("msgid \"Unpublished\"\nmsgstr \"Non publié\"");

        translator.deleteTranslations("fr");
        assertThat(badge("/fr" + NodeEntityType.path(draft))).isEqualTo("Unpublished");

        MockHttpServletResponse imported = mockMvc.perform(multipart(TranslationController.IMPORT_PATH)
                .file(new MockMultipartFile(TranslationController.FILE, "springdrop.fr.po", "text/plain",
                        po.getBytes(StandardCharsets.UTF_8)))
                .param(TranslationController.LANGCODE, "fr").with(csrf()).with(translatorAccount()))
                .andReturn().getResponse();
        assertThat(imported.getRedirectedUrl()).isEqualTo(TranslationController.PATH + "?langcode=fr");
        assertThat(badge("/fr" + NodeEntityType.path(draft))).isEqualTo("Non publié");
    }

    @Test
    void clearingATranslationsBoxRemovesItAndAnUnchangedOneIsLeft() throws Exception {
        translator.save("fr", "Next", "", "Suivant");
        translator.save("fr", "Previous", "", "Précédent");
        Document translate = page(TranslationController.PATH);
        Element next = row(translate, "Next");
        Element previous = row(translate, "Previous");

        mockMvc.perform(post(TranslationController.PATH).param(TranslationController.LANGCODE, "fr")
                .param(TranslationController.IDS, next.attr("data-string") + "," + previous.attr("data-string")
                        + ",999999")
                .param(next.selectFirst("textarea").attr("name"), "")
                .param(previous.selectFirst("textarea").attr("name"), "Précédent")
                .with(csrf()).with(translatorAccount()));

        assertThat(translator.translation("fr", "Next", "")).isEmpty();
        assertThat(translator.translation("fr", "Previous", "")).contains("Précédent");
    }

    @Test
    void aPluralStringHasABoxForEachForm() throws Exception {
        translator.record("1 comment" + Translator.PLURAL_DELIMITER + "@count comments", "");
        Document translate = page(TranslationController.PATH);
        Element plural = translate.selectFirst("table.translate-strings tbody tr");

        assertThat(plural.select("td div").eachText()).containsExactly("1 comment", "@count comments");
        assertThat(plural.select("textarea")).hasSize(2);

        mockMvc.perform(post(TranslationController.PATH).param(TranslationController.LANGCODE, "fr")
                .param(TranslationController.IDS, plural.attr("data-string"))
                .param(plural.select("textarea").get(0).attr("name"), "1 commentaire")
                .param(plural.select("textarea").get(1).attr("name"), "@count commentaires")
                .with(csrf()).with(translatorAccount()));

        assertThat(translator.translated("fr")).singleElement().satisfies(string -> assertThat(
                string.translation()).isEqualTo("1 commentaire" + Translator.PLURAL_DELIMITER
                + "@count commentaires"));
    }

    @Test
    void theTranslatePageFiltersAndShowsContexts() throws Exception {
        translator.record("May", "Long month name");
        translator.save("fr", "Next", "", "Suivant");

        Document all = page(TranslationController.PATH + "?langcode=fr");
        assertThat(all.select("table.translate-strings small").eachText()).containsExactly(
                "In context: Long month name");
        assertThat(page(TranslationController.PATH + "?langcode=fr&untranslated=true")
                .select("table.translate-strings tbody tr")).hasSize(1);
        assertThat(page(TranslationController.PATH + "?langcode=fr&search=zzz").text())
                .contains("No strings available.");
        BootstrapAssertions.assertNoOutlineButtons(all);
    }

    @Test
    void theTranslatePageIsPaged() throws Exception {
        for (int number = 0; number < TranslationController.PAGE_SIZE + 1; number++) {
            translator.record(String.format("String %03d", number), "");
        }

        Document first = page(TranslationController.PATH);
        assertThat(first.select("table.translate-strings tbody tr")).hasSize(TranslationController.PAGE_SIZE);
        assertThat(first.select(".pagination a").eachAttr("href")).contains(
                TranslationController.PATH + "?langcode=fr&search=&untranslated=&page=2");
        assertThat(page(TranslationController.PATH + "?page=2").select("table.translate-strings tbody tr"))
                .hasSize(1);
        assertThat(page(TranslationController.PATH + "?page=x").select("table.translate-strings tbody tr"))
                .hasSize(TranslationController.PAGE_SIZE);
    }

    @Test
    void aSiteWithEnglishAloneHasNothingToTranslate() throws Exception {
        languages.delete("fr");

        assertThat(page(TranslationController.PATH).selectFirst(".no-languages")).isNotNull();
        assertThat(mockMvc.perform(post(TranslationController.PATH).param(TranslationController.LANGCODE, "en")
                .with(csrf()).with(translatorAccount())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(TranslationController.EXPORT_PATH + "/en").with(translatorAccount()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void anImportWithoutALanguageOrAFileOrWithAFileItCannotReadIsRefused() throws Exception {
        assertThat(page(TranslationController.IMPORT_PATH).select("select[name=langcode] option").eachAttr("value"))
                .containsExactly("fr");

        Document missing = Jsoup.parse(mockMvc.perform(multipart(TranslationController.IMPORT_PATH)
                .param(TranslationController.LANGCODE, "en").with(csrf()).with(translatorAccount()))
                .andReturn().getResponse().getContentAsString());
        assertThat(missing.select(".import-error").eachText()).containsExactly(TranslationController.CHOOSE_LANGUAGE,
                TranslationController.CHOOSE_FILE);

        Document empty = Jsoup.parse(mockMvc.perform(multipart(TranslationController.IMPORT_PATH)
                .file(new MockMultipartFile(TranslationController.FILE, "empty.po", "text/plain", new byte[0]))
                .param(TranslationController.LANGCODE, "fr").with(csrf()).with(translatorAccount()))
                .andReturn().getResponse().getContentAsString());
        assertThat(empty.select(".import-error").eachText()).containsExactly(TranslationController.CHOOSE_FILE);

        Document malformed = Jsoup.parse(mockMvc.perform(multipart(TranslationController.IMPORT_PATH)
                .file(new MockMultipartFile(TranslationController.FILE, "bad.po", "text/plain",
                        "msgid".getBytes(StandardCharsets.UTF_8)))
                .param(TranslationController.LANGCODE, "fr").with(csrf()).with(translatorAccount()))
                .andReturn().getResponse().getContentAsString());
        assertThat(malformed.select(".import-error").eachText()).containsExactly(
                "The file could not be read. Line 1: expected a keyword and a quoted string");
    }

    @Test
    void theExportPageOffersEachTranslatableLanguage() throws Exception {
        Document export = page(TranslationController.EXPORT_PATH);

        assertThat(export.select(".export-languages a").eachAttr("href"))
                .containsExactly(TranslationController.EXPORT_PATH + "/fr");
    }

    @Test
    void translatingIsClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(TranslationController.PATH).with(user("visitor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }
}
