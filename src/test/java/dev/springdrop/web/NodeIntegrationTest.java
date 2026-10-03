package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.types.StringFormatter;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.permission.PermissionRegistry;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class NodeIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUBTITLE = "subtitle";

    private static final String SUMMARY = "summary";

    private static final long AUTHOR = 7L;

    private static final long SOMEONE_ELSE = 8L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ViewDisplayManager viewDisplays;

    @Autowired
    private RenderService renderer;

    @Autowired
    private RoleManager roles;

    @Autowired
    private PermissionRegistry permissions;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @BeforeEach
    void noContentYet() {
        menus.install();
        storedLinks.install();
        roles.install();
        nodes.install();
        clearContent();
    }

    @AfterEach
    void noContentLeft() {
        clearContent();
    }

    private void clearContent() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        List.of(SUBTITLE, SUMMARY).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        viewDisplays.delete(NodeEntityType.ID, ARTICLE, ViewDisplayConfig.TEASER_MODE);
        types.all().forEach(type -> types.delete(type.id()));
        roles.find(RoleConfig.ANONYMOUS).ifPresent(anonymous -> anonymous.permissions()
                .forEach(permission -> roles.revoke(RoleConfig.ANONYMOUS, permission)));
    }

    private static RequestPostProcessor typeAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(NodePermissions.ADMINISTER_CONTENT_TYPES));
    }

    private static RequestPostProcessor account(long id, String... granted) {
        return user(new AccountPrincipal(id, "account" + id, "", true, List.of(granted)));
    }

    private static RequestPostProcessor author() {
        return account(AUTHOR, NodePermissions.ACCESS_CONTENT, NodePermissions.create(ARTICLE),
                NodePermissions.editOwn(ARTICLE));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document submit(MockHttpServletRequestBuilder request, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(who).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String redirectOf(MockHttpServletRequestBuilder request, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(request.with(who).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
    }

    private void anArticleTypeWithASubtitleAndASummary() {
        types.save(NodeType.of(ARTICLE, "Article").describedAs("News and announcements."));
        fields.createStorage(new FieldStorageConfig(SUBTITLE, NodeEntityType.ID, StringFieldType.ID, 1,
                Map.of("max_length", 40)));
        fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, ARTICLE, "Subtitle"));
        fields.createStorage(new FieldStorageConfig(SUMMARY, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
    }

    private EntityData written(long owner, String title, boolean published) {
        Map<String, Object> values = new java.util.LinkedHashMap<>(
                nodes.create(types.find(ARTICLE).orElseThrow(), owner).fields());
        values.put(BaseFieldDefinition.STATUS, published);
        values.put(SUBTITLE, "From the front desk");
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, values), owner);
    }

    private static long idOf(EntityData node) {
        return ((Number) node.id()).longValue();
    }

    private static long idFrom(String redirect) {
        return Long.parseLong(redirect.substring("/node/".length()));
    }

    @Nested
    class ContentTypes {

        @Test
        void theContentTypesAreClosedToSomeoneWithoutThePermission() throws Exception {
            mockMvc.perform(get(NodeTypeController.PATH).with(user("visitor")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(NodeTypeController.PATH + "/add").with(user("visitor")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void eachTypeIsListedWithItsActionsAsButtons() throws Exception {
            types.save(NodeType.of(ARTICLE, "Article").describedAs("News and announcements."));

            Document document = page(NodeTypeController.PATH, typeAdministrator());

            assertThat(document.select("tbody td:first-child").text()).isEqualTo("Article");
            assertThat(document.selectFirst("a:contains(Manage fields)").attr("href"))
                    .isEqualTo(FieldUiController.fieldsPath(NodeEntityType.ID, ARTICLE));
            BootstrapAssertions.assertNoOutlineButtons(document);
            BootstrapAssertions.assertEditControlsAreButtons(document);
        }

        @Test
        void anEmptyListSaysSo() throws Exception {
            assertThat(page(NodeTypeController.PATH, typeAdministrator()).text())
                    .contains("There are no content types yet.");
        }

        @Test
        void addingATypeStoresEverySettingAndOpensItsFields() throws Exception {
            String redirect = redirectOf(post(NodeTypeController.PATH + "/add")
                    .param(NodeTypeController.LABEL, "Press release")
                    .param(NodeTypeController.DESCRIPTION, "Statements for the press.")
                    .param(NodeTypeController.HELP, "Write in the third person.")
                    .param(NodeTypeController.TITLE_LABEL, "Headline")
                    .param(NodeTypeController.STICKY, "on")
                    .param(NodeTypeController.NEW_REVISION, "on"), typeAdministrator());

            assertThat(redirect).isEqualTo(FieldUiController.fieldsPath(NodeEntityType.ID, "press_release"));
            assertThat(types.find("press_release")).contains(new NodeType("press_release", "Press release",
                    "Statements for the press.", "Write in the third person.", "Headline",
                    false, false, true, true));
        }

        @Test
        void theAddFormStartsFromTheUsualDefaults() throws Exception {
            Document document = page(NodeTypeController.PATH + "/add", typeAdministrator());

            assertThat(document.selectFirst("input[name=" + NodeTypeController.TITLE_LABEL + "]").val())
                    .isEqualTo(NodeType.DEFAULT_TITLE_LABEL);
            assertThat(document.selectFirst("input[name=" + NodeTypeController.PUBLISHED + "]").hasAttr("checked"))
                    .isTrue();
            assertThat(document.selectFirst("input[name=" + NodeTypeController.STICKY + "]").hasAttr("checked"))
                    .isFalse();
        }

        @Test
        void aTypeNamedLikeAnotherGetsItsOwnMachineName() throws Exception {
            types.save(NodeType.of(ARTICLE, "Article"));

            String redirect = redirectOf(post(NodeTypeController.PATH + "/add")
                    .param(NodeTypeController.LABEL, "Article")
                    .param(NodeTypeController.TITLE_LABEL, "Title"), typeAdministrator());

            assertThat(redirect).isEqualTo(FieldUiController.fieldsPath(NodeEntityType.ID, "article_2"));
        }

        @Test
        void aTypeWithoutANameIsNotAdded() throws Exception {
            Document document = submit(post(NodeTypeController.PATH + "/add")
                    .param(NodeTypeController.LABEL, "")
                    .param(NodeTypeController.TITLE_LABEL, "Title"), typeAdministrator());

            assertThat(document.selectFirst("input[name=" + NodeTypeController.LABEL + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(types.all()).isEmpty();
        }

        @Test
        void editingATypeKeepsItsMachineNameAndChangesItsSettings() throws Exception {
            types.save(NodeType.of(ARTICLE, "Article"));
            assertThat(page(NodeTypeController.managePath(ARTICLE), typeAdministrator())
                    .selectFirst("input[name=" + NodeTypeController.LABEL + "]").val()).isEqualTo("Article");

            redirectOf(post(NodeTypeController.managePath(ARTICLE))
                    .param(NodeTypeController.LABEL, "Story")
                    .param(NodeTypeController.TITLE_LABEL, "Title"), typeAdministrator());

            assertThat(types.find(ARTICLE)).hasValueSatisfying(type -> {
                assertThat(type.label()).isEqualTo("Story");
                assertThat(type.published()).isFalse();
            });
        }

        @Test
        void anUnknownTypeIsNotFound() throws Exception {
            mockMvc.perform(get(NodeTypeController.managePath("missing")).with(typeAdministrator()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anUnusedTypeIsDeletedOnceConfirmed() throws Exception {
            types.save(NodeType.of(ARTICLE, "Article"));
            assertThat(page(NodeTypeController.managePath(ARTICLE) + "/delete", typeAdministrator())
                    .select("button[type=submit]")).isNotEmpty();

            assertThat(redirectOf(post(NodeTypeController.managePath(ARTICLE) + "/delete"), typeAdministrator()))
                    .isEqualTo(NodeTypeController.PATH);
            assertThat(types.find(ARTICLE)).isEmpty();
        }

        @Test
        void aTypeWithContentCannotBeDeleted() throws Exception {
            anArticleTypeWithASubtitleAndASummary();
            written(AUTHOR, "Spring schedule", true);

            assertThat(page(NodeTypeController.managePath(ARTICLE) + "/delete", typeAdministrator())
                    .select("button[type=submit]")).isEmpty();
            redirectOf(post(NodeTypeController.managePath(ARTICLE) + "/delete"), typeAdministrator());

            assertThat(types.find(ARTICLE)).isPresent();
        }

        @Test
        void eachTypeOffersItsOwnPermissionsOnceSaved() {
            types.save(NodeType.of(ARTICLE, "Article"));

            assertThat(permissions.providedBy(NodePermissions.PROVIDER))
                    .extracting(permission -> permission.name())
                    .contains(
                            NodePermissions.create(ARTICLE),
                            NodePermissions.editOwn(ARTICLE),
                            NodePermissions.editAny(ARTICLE),
                            NodePermissions.deleteOwn(ARTICLE),
                            NodePermissions.deleteAny(ARTICLE));
        }
    }

    @Nested
    class WritingContent {

        @BeforeEach
        void anArticleType() {
            anArticleTypeWithASubtitleAndASummary();
        }

        @Test
        void theAddPageListsOnlyTheTypesThePersonMayCreate() throws Exception {
            types.save(NodeType.of("page", "Basic page"));

            Document document = page(NodeController.ADD_PATH, author());

            assertThat(document.select(".list-group-item .fw-semibold").eachText()).containsExactly("Article");
            assertThat(document.text()).contains("News and announcements.");
        }

        @Test
        void theAddPageSaysSoWhenNoTypeMayBeCreated() throws Exception {
            assertThat(page(NodeController.ADD_PATH, account(SOMEONE_ELSE)).text())
                    .contains("There is no content type you may create content of.");
        }

        @Test
        void contentOfATypeThePersonMayNotCreateIsRefused() throws Exception {
            mockMvc.perform(get(NodeController.ADD_PATH + "/" + ARTICLE).with(account(SOMEONE_ELSE)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE).with(account(SOMEONE_ELSE)).with(csrf())
                            .param(NodeController.TITLE, "Spring schedule"))
                    .andExpect(status().isForbidden());
            assertThat(queries.query(NodeEntityType.ID).count()).isZero();
        }

        @Test
        void contentOfAnUnknownTypeIsNotFound() throws Exception {
            mockMvc.perform(get(NodeController.ADD_PATH + "/missing").with(author()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void theAddFormNamesTheTitleTheWayTheTypeDoesAndShowsItsGuidelines() throws Exception {
            types.save(types.find(ARTICLE).orElseThrow()
                    .withTitleLabel("Headline").withHelp("Write in the third person."));

            Document document = page(NodeController.ADD_PATH + "/" + ARTICLE, author());

            assertThat(document.selectFirst("label[for=" + NodeController.TITLE + "]").text()).startsWith("Headline");
            assertThat(document.text()).contains("Write in the third person.");
            assertThat(document.selectFirst("input[name=" + SUBTITLE + "]")).isNotNull();
        }

        @Test
        void aNewNodeStartsOutTheWayItsTypeSaysAndRecordsWhoWroteIt() throws Exception {
            types.save(types.find(ARTICLE).orElseThrow().withDefaults(false, false, true, true));

            String redirect = redirectOf(post(NodeController.ADD_PATH + "/" + ARTICLE)
                    .param(NodeController.TITLE, "Spring schedule"), author());

            assertThat(nodes.find(idFrom(redirect))).hasValueSatisfying(node -> {
                assertThat(node.label()).isEqualTo("Spring schedule");
                assertThat(node.fields()).containsEntry(BaseFieldDefinition.STATUS, false)
                        .containsEntry(NodeEntityType.PROMOTE, false)
                        .containsEntry(NodeEntityType.STICKY, true)
                        .containsEntry(BaseFieldDefinition.OWNER, AUTHOR)
                        .containsEntry(NodeEntityType.REVISION_USER, AUTHOR)
                        .containsKeys(BaseFieldDefinition.CREATED, BaseFieldDefinition.CHANGED);
            });
        }

        @Test
        void contentWrittenBySomeoneNotSignedInIsOwnedByTheAnonymousAccount() throws Exception {
            roles.grant(RoleConfig.ANONYMOUS, NodePermissions.create(ARTICLE));

            String redirect = mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE).with(csrf())
                            .param(NodeController.TITLE, "Lost and found"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();

            assertThat(nodes.find(idFrom(redirect))).hasValueSatisfying(node ->
                    assertThat(node.fields()).containsEntry(BaseFieldDefinition.OWNER, UserAccount.ANONYMOUS_ID));
        }

        @Test
        void aNodeWithoutATitleIsNotSaved() throws Exception {
            Document document = submit(post(NodeController.ADD_PATH + "/" + ARTICLE)
                    .param(NodeController.TITLE, "")
                    .param(SUBTITLE, "From the front desk"), author());

            assertThat(document.selectFirst("input[name=" + NodeController.TITLE + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(document.selectFirst("input[name=" + SUBTITLE + "]").val()).isEqualTo("From the front desk");
            assertThat(queries.query(NodeEntityType.ID).count()).isZero();
        }

        @Test
        void aFieldValueTheFieldDoesNotAllowIsNotSaved() throws Exception {
            Document document = submit(post(NodeController.ADD_PATH + "/" + ARTICLE)
                    .param(NodeController.TITLE, "Spring schedule")
                    .param(SUBTITLE, "x".repeat(41)), author());

            assertThat(document.selectFirst("input[name=" + SUBTITLE + "]").hasClass("is-invalid")).isTrue();
            assertThat(queries.query(NodeEntityType.ID).count()).isZero();
        }

        @Test
        void editingANodeShowsWhatItHoldsAndSavesTheChange() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));
            Document form = page(NodeEntityType.editPath(id), author());
            assertThat(form.selectFirst("input[name=" + NodeController.TITLE + "]").val()).isEqualTo("Spring schedule");
            assertThat(form.selectFirst("input[name=" + SUBTITLE + "]").val()).isEqualTo("From the front desk");

            String redirect = redirectOf(post(NodeEntityType.editPath(id))
                    .param(NodeController.TITLE, "Summer schedule")
                    .param(SUBTITLE, "From the office"), author());

            assertThat(redirect).isEqualTo(NodeEntityType.path(id));
            assertThat(nodes.find(id)).hasValueSatisfying(node -> {
                assertThat(node.label()).isEqualTo("Summer schedule");
                assertThat(node.fields()).containsEntry(SUBTITLE, "From the office")
                        .containsEntry(BaseFieldDefinition.OWNER, AUTHOR);
            });
        }

        @Test
        void aFieldLeftEmptyOnEditIsEmptied() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));

            redirectOf(post(NodeEntityType.editPath(id)).param(NodeController.TITLE, "Spring schedule"), author());

            assertThat(nodes.find(id)).hasValueSatisfying(node -> assertThat(node.fields()).doesNotContainKey(SUBTITLE));
        }

        @Test
        void anInvalidEditIsNotSaved() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));

            submit(post(NodeEntityType.editPath(id)).param(NodeController.TITLE, ""), author());

            assertThat(nodes.find(id)).hasValueSatisfying(node -> assertThat(node.label()).isEqualTo("Spring schedule"));
        }

        @Test
        void someoneElsesNodeCannotBeEditedWithEditOwn() throws Exception {
            long id = idOf(written(SOMEONE_ELSE, "Spring schedule", true));

            mockMvc.perform(get(NodeEntityType.editPath(id)).with(author())).andExpect(status().isForbidden());
            mockMvc.perform(post(NodeEntityType.editPath(id)).with(author()).with(csrf())
                            .param(NodeController.TITLE, "Taken over"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aNodeWhoseTypeIsGoneCannotBeEdited() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));
            types.delete(ARTICLE);

            mockMvc.perform(get(NodeEntityType.editPath(id)).with(author())).andExpect(status().isNotFound());
        }
    }

    @Nested
    class ReadingContent {

        @BeforeEach
        void anArticleType() {
            anArticleTypeWithASubtitleAndASummary();
        }

        @Test
        void aNodeIsShownOnItsOwnPageWithItsTitleAsThePageTitle() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));

            Document document = page(NodeEntityType.path(id), author());

            assertThat(document.selectFirst("h1").text()).isEqualTo("Spring schedule");
            assertThat(document.selectFirst("article.node--full .node__content").text())
                    .contains("From the front desk");
            assertThat(document.select("article.node h2")).isEmpty();
        }

        @Test
        void theEditTabShowsOnlyToSomeoneWhoMayEdit() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));

            assertThat(page(NodeEntityType.path(id), author()).select("a[href=" + NodeEntityType.editPath(id) + "]"))
                    .isNotEmpty();
            assertThat(page(NodeEntityType.path(id), account(SOMEONE_ELSE, NodePermissions.ACCESS_CONTENT))
                    .select("a[href=" + NodeEntityType.editPath(id) + "]")).isEmpty();
        }

        @Test
        void anUnknownNodeIsNotFound() throws Exception {
            mockMvc.perform(get(NodeEntityType.path(999_999)).with(author())).andExpect(status().isNotFound());
        }

        @Test
        void anUnpublishedNodeIsClosedToSomeoneElse() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", false));

            mockMvc.perform(get(NodeEntityType.path(id)).with(account(SOMEONE_ELSE, NodePermissions.ACCESS_CONTENT)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anUnpublishedNodeIsMarkedSoForItsOwner() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", false));

            assertThat(page(NodeEntityType.path(id), account(AUTHOR, NodePermissions.VIEW_OWN_UNPUBLISHED)).text())
                    .contains("Unpublished");
        }

        @Test
        void someoneNotSignedInReadsContentWhenTheAnonymousRoleMayAccessIt() throws Exception {
            long id = idOf(written(AUTHOR, "Spring schedule", true));
            mockMvc.perform(get(NodeEntityType.path(id))).andExpect(status().isForbidden());

            roles.grant(RoleConfig.ANONYMOUS, NodePermissions.ACCESS_CONTENT);

            mockMvc.perform(get(NodeEntityType.path(id))).andExpect(status().isOk());
        }
    }

    @Test
    void aTypeWithFieldsAndDisplaysGivesWorkingFormsAndTeaserAndFullOutput() throws Exception {
        redirectOf(post(NodeTypeController.PATH + "/add")
                .param(NodeTypeController.LABEL, "Article")
                .param(NodeTypeController.TITLE_LABEL, "Title")
                .param(NodeTypeController.PUBLISHED, "on"), typeAdministrator());
        fields.createStorage(new FieldStorageConfig(SUBTITLE, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, ARTICLE, "Subtitle"));
        fields.createStorage(new FieldStorageConfig(SUMMARY, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        viewDisplays.save(ViewDisplayConfig.of(NodeEntityType.ID, ARTICLE, ViewDisplayConfig.TEASER_MODE)
                .with(FieldDisplaySlot.of(SUBTITLE, StringFormatter.ID, 0))
                .withoutField(SUMMARY));

        long id = idFrom(redirectOf(post(NodeController.ADD_PATH + "/" + ARTICLE)
                .param(NodeController.TITLE, "Spring schedule")
                .param(SUBTITLE, "From the front desk")
                .param(SUMMARY, "Opening hours change in May."), author()));

        Document full = page(NodeEntityType.path(id), author());
        assertThat(full.selectFirst(".node__content").text())
                .contains("From the front desk", "Opening hours change in May.");

        EntityData node = nodes.find(id).orElseThrow();
        Document teaser = Jsoup.parseBodyFragment(
                renderer.render(nodes.build(node, ViewDisplayConfig.TEASER_MODE, false)).html());
        assertThat(teaser.selectFirst("article.node--teaser h2 a").attr("href")).isEqualTo(NodeEntityType.path(id));
        assertThat(teaser.selectFirst(".node__content").text())
                .contains("From the front desk")
                .doesNotContain("Opening hours change in May.");
    }

    @Test
    void whatANodeRendersIsTaggedWithTheNode() {
        anArticleTypeWithASubtitleAndASummary();
        EntityData node = written(AUTHOR, "Spring schedule", true);

        assertThat(renderer.render(nodes.build(node, ViewDisplayConfig.FULL_MODE, true)).cache().tags())
                .contains(NodeService.cacheTag(node.id()));
    }

    @Test
    void contentTypesAreListedByLabel() {
        types.save(NodeType.of("page", "Basic page"));
        types.save(NodeType.of(ARTICLE, "Article"));

        List<String> labels = new ArrayList<>();
        types.all().forEach(type -> labels.add(type.label()));

        assertThat(labels).containsExactly("Article", "Basic page");
    }
}
