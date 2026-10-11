package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.TranslatableFields;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class NodeTranslationIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUMMARY = "summary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private TranslatableFields translatableFields;

    private long id;

    @BeforeEach
    void anArticleOnAFrenchSite() {
        clear();
        languages.save(new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr", ""));
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(FieldStorageConfig.single(SUMMARY, NodeEntityType.ID, StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        translatableFields.set(NodeEntityType.ID, SUMMARY, true);
        id = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule",
                Map.of(SUMMARY, "Opening hours", "status", true, "owner", 1L)), 1L).id()).longValue();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(each -> nodes.delete(((Number) each).longValue()));
        fields.findStorage(NodeEntityType.ID, SUMMARY)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, SUMMARY));
        translatableFields.set(NodeEntityType.ID, SUMMARY, false);
        types.all().forEach(type -> types.delete(type.id()));
        languages.delete("fr");
    }

    private static RequestPostProcessor translatorAccount(String... more) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.editAny(ARTICLE), Permissions.TRANSLATE_CONTENT));
        granted.addAll(List.of(more));
        return user(new AccountPrincipal(5L, "translator", "", true, granted));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who)).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletResponse send(String path, RequestPostProcessor who, String... fields) throws Exception {
        var request = post(path);
        for (int index = 0; index < fields.length; index += 2) {
            request.param(fields[index], fields[index + 1]);
        }
        return mockMvc.perform(request.with(csrf()).with(who)).andReturn().getResponse();
    }

    @Test
    void aNodeIsTranslatedThroughItsOverviewAndShownInEachLanguage() throws Exception {
        Document view = page(NodeEntityType.path(id), translatorAccount());
        assertThat(view.select("a").eachText()).contains("Translate");

        Document overview = page(NodeTranslationController.overviewPath(id), translatorAccount());
        assertThat(overview.select("tr[data-language]").eachAttr("data-language")).containsExactly("en", "fr");
        assertThat(overview.selectFirst("tr[data-language=en]").text()).contains("Original language");
        assertThat(overview.selectFirst("tr[data-language=fr]").text()).contains("Not translated");
        BootstrapAssertions.assertNoOutlineButtons(overview);
        BootstrapAssertions.assertEditControlsAreButtons(overview);

        MockHttpServletResponse added = send(NodeTranslationController.addPath(id, "fr"), translatorAccount());
        assertThat(added.getRedirectedUrl()).isEqualTo(NodeEntityType.editPath(id) + "?langcode=fr");

        Document form = page(added.getRedirectedUrl(), translatorAccount());
        assertThat(form.selectFirst("input[name=title]").val()).isEqualTo("Spring schedule");
        assertThat(form.selectFirst("form").attr("action")).isEqualTo(NodeEntityType.editPath(id) + "?langcode=fr");
        send(NodeEntityType.editPath(id) + "?langcode=fr", translatorAccount(), NodeController.TITLE,
                "Horaires du printemps", SUMMARY, "Heures d'ouverture");

        assertThat(page("/fr" + NodeEntityType.path(id), translatorAccount()).title())
                .startsWith("Horaires du printemps");
        assertThat(page(NodeEntityType.path(id), translatorAccount()).title()).startsWith("Spring schedule");
        assertThat(nodes.find(id, "fr").orElseThrow().fields()).containsEntry(SUMMARY, "Heures d'ouverture");
        assertThat(nodes.find(id, "en").orElseThrow().fields()).containsEntry(SUMMARY, "Opening hours");
        assertThat(page(NodeTranslationController.overviewPath(id), translatorAccount())
                .selectFirst("tr[data-language=fr]").text()).contains("Horaires du printemps", "Translated");
        assertThat(page("/fr" + NodeEntityType.editPath(id), translatorAccount())
                .selectFirst("input[name=title]").val()).isEqualTo("Horaires du printemps");
    }

    @Test
    void aTranslationIsDeletedAfterConfirmation() throws Exception {
        send(NodeTranslationController.addPath(id, "fr"), translatorAccount());

        Document confirm = page(NodeTranslationController.deletePath(id, "fr"), translatorAccount());
        assertThat(confirm.title()).isEqualTo("Delete the French translation of Spring schedule?");
        MockHttpServletResponse deleted = send(NodeTranslationController.deletePath(id, "fr"), translatorAccount());

        assertThat(deleted.getRedirectedUrl()).isEqualTo(NodeTranslationController.overviewPath(id));
        assertThat(entities.translations(NodeEntityType.ID, id)).containsExactly("en");
    }

    @Test
    void theOriginalCannotBeDeletedAndAnExistingTranslationCannotBeAddedAgain() throws Exception {
        send(NodeTranslationController.addPath(id, "fr"), translatorAccount());

        assertThat(send(NodeTranslationController.addPath(id, "fr"), translatorAccount()).getStatus())
                .isEqualTo(404);
        assertThat(send(NodeTranslationController.addPath(id, "de"), translatorAccount()).getStatus())
                .isEqualTo(404);
        assertThat(mockMvc.perform(get(NodeTranslationController.deletePath(id, "en")).with(translatorAccount()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(NodeTranslationController.overviewPath(999_999)).with(translatorAccount()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void translatingNeedsThePermissionAndMoreThanOneLanguage() throws Exception {
        RequestPostProcessor editor = user(new AccountPrincipal(6L, "editor", "", true,
                List.of(NodePermissions.ACCESS_CONTENT, NodePermissions.editAny(ARTICLE))));

        assertThat(mockMvc.perform(get(NodeTranslationController.overviewPath(id)).with(editor)).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(page(NodeEntityType.path(id), editor).select("a").eachText()).doesNotContain("Translate");

        RequestPostProcessor readerWhoTranslates = user(new AccountPrincipal(7L, "reader", "", true,
                List.of(NodePermissions.ACCESS_CONTENT, Permissions.TRANSLATE_CONTENT)));
        assertThat(mockMvc.perform(get(NodeTranslationController.overviewPath(id)).with(readerWhoTranslates))
                .andReturn().getResponse().getStatus()).isEqualTo(403);

        languages.delete("fr");
        assertThat(page(NodeEntityType.path(id), translatorAccount()).select("a").eachText())
                .doesNotContain("Translate");
    }

    @Test
    void theFirstAccountMayTranslateWithoutThePermission() throws Exception {
        RequestPostProcessor first = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true,
                List.of()));

        assertThat(mockMvc.perform(get(NodeTranslationController.overviewPath(id)).with(first)).andReturn()
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void theFieldUiMarksAFieldTranslatable() throws Exception {
        RequestPostProcessor builder = user("builder").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_FIELDS));
        String path = FieldUiController.fieldsPath(NodeEntityType.ID, ARTICLE) + "/" + SUMMARY;

        assertThat(page(path, builder).selectFirst("input[name=translatable]").hasAttr("checked")).isTrue();
        send(path, builder, FieldUiController.LABEL, "Summary", FieldUiController.CARDINALITY, "1");

        assertThat(translatableFields.translatable(NodeEntityType.ID, SUMMARY)).isFalse();
        send(path, builder, FieldUiController.LABEL, "Summary", FieldUiController.CARDINALITY, "1",
                FieldUiController.TRANSLATABLE, "true");
        assertThat(translatableFields.translatable(NodeEntityType.ID, SUMMARY)).isTrue();
    }
}
