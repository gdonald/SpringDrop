package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.moderation.ModerationConfig;
import dev.springdrop.kernel.moderation.ModerationStateSettings;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.kernel.workflow.types.ContentModerationWorkflowType;
import dev.springdrop.support.AbstractIntegrationTest;
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
class ContentModerationIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String PAGE = "page";

    private static final String EDITORIAL = ContentModerationService.EDITORIAL;

    private static final String DRAFT = ContentModerationWorkflowType.DRAFT;

    private static final String PUBLISHED = ContentModerationWorkflowType.PUBLISHED;

    private static final String ARCHIVED = ContentModerationService.ARCHIVED;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ContentModerationService moderations;

    @Autowired
    private WorkflowManager workflows;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private long authorId;

    @BeforeEach
    void articlesUnderTheEditorialWorkflow() {
        menus.install();
        storedLinks.install();
        accounts.install();
        clear();
        authorId = accounts.create("edith", "edith@example.com", "").id();
        types.save(NodeType.of(ARTICLE, "Article"));
        types.save(NodeType.of(PAGE, "Basic page"));
        moderations.installEditorial();
        moderations.saveConfig(moderations.config(EDITORIAL).orElseThrow().withBundles(List.of(ARTICLE)));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
        configStore.listNames(WorkflowConfig.CONFIG_PREFIX).forEach(configStore::delete);
        configStore.listNames(ModerationConfig.CONFIG_PREFIX).forEach(configStore::delete);
        accounts.findByName("edith").ifPresent(account -> entities.delete("user", account.id()));
    }

    private RequestPostProcessor editor(String... transitions) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.create(ARTICLE), NodePermissions.editOwn(ARTICLE),
                NodePermissions.VIEW_LATEST_VERSION, NodePermissions.VIEW_ANY_UNPUBLISHED));
        for (String transition : transitions) {
            granted.add(WorkflowManager.transitionPermission(EDITORIAL, transition));
        }
        return user(new AccountPrincipal(authorId, "edith", "", true, granted));
    }

    private RequestPostProcessor fullEditor() {
        return editor("create_new_draft", "publish", "archive", "archived_draft", "archived_published");
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private long created(String title, String state) throws Exception {
        String redirect = mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE).with(fullEditor()).with(csrf())
                        .param(NodeController.TITLE, title)
                        .param(NodeController.MODERATION_STATE, state))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return Long.parseLong(redirect.substring("/node/".length()));
    }

    private void edited(long id, String title, String state) throws Exception {
        mockMvc.perform(post(NodeEntityType.editPath(id)).with(fullEditor()).with(csrf())
                        .param(NodeController.TITLE, title)
                        .param(NodeController.MODERATION_STATE, state))
                .andExpect(redirectedUrl(NodeEntityType.path(id)));
    }

    private EntityData live(long id) {
        return nodes.find(id).orElseThrow();
    }

    private EntityData latest(long id) {
        return nodes.latestRevision(live(id));
    }

    @Test
    void aDraftOfAPublishedNodeLeavesTheLiveRevisionAloneAndAppearsOnTheDashboard() throws Exception {
        long id = created("Spring schedule", PUBLISHED);

        edited(id, "Summer schedule", DRAFT);

        assertThat(live(id).label()).isEqualTo("Spring schedule");
        assertThat(live(id).fields()).containsEntry(BaseFieldDefinition.STATUS, true);
        assertThat(latest(id).label()).isEqualTo("Summer schedule");
        assertThat(ContentModerationService.stateOf(latest(id))).isEqualTo(DRAFT);
        assertThat(page(NodeEntityType.path(id), fullEditor()).selectFirst("h1").text()).isEqualTo("Spring schedule");

        Document dashboard = page(ModerationController.DASHBOARD_PATH, fullEditor());
        assertThat(dashboard.selectFirst("tr[data-node=" + id + "]").text()).contains("Summer schedule", "Draft");
        assertThat(dashboard.selectFirst("tr[data-node=" + id + "] a").attr("href"))
                .isEqualTo(NodeController.latestPath(id));
    }

    @Nested
    class MovingBetweenStates {

        @Test
        void aNodeNeverPublishedKeepsItsDraftsAsTheRevisionTheSiteShows() throws Exception {
            long id = created("Spring schedule", DRAFT);

            edited(id, "Spring schedule, revised", DRAFT);

            assertThat(live(id).label()).isEqualTo("Spring schedule, revised");
            assertThat(live(id).fields()).containsEntry(BaseFieldDefinition.STATUS, false);
        }

        @Test
        void publishingTheDraftMakesItLive() throws Exception {
            long id = created("Spring schedule", PUBLISHED);
            edited(id, "Summer schedule", DRAFT);

            edited(id, "Summer schedule", PUBLISHED);

            assertThat(live(id).label()).isEqualTo("Summer schedule");
            assertThat(live(id).revisionId()).isEqualTo(latest(id).revisionId());
        }

        @Test
        void archivingUnpublishesTheNodeAsTheRevisionTheSiteShows() throws Exception {
            long id = created("Spring schedule", PUBLISHED);

            edited(id, "Spring schedule", ARCHIVED);

            assertThat(live(id).fields()).containsEntry(BaseFieldDefinition.STATUS, false)
                    .containsEntry(NodeEntityType.MODERATION_STATE, ARCHIVED);
        }

        @Test
        void theEditFormStartsFromTheNewestRevisionAndOffersOnlyTheStatesThePersonMayReach() throws Exception {
            long id = created("Spring schedule", PUBLISHED);
            edited(id, "Summer schedule", DRAFT);

            Document form = page(NodeEntityType.editPath(id), editor("create_new_draft"));

            assertThat(form.selectFirst("input[name=" + NodeController.TITLE + "]").val()).isEqualTo("Summer schedule");
            assertThat(form.select("select[name=" + NodeController.MODERATION_STATE + "] option").eachAttr("value"))
                    .containsExactly(DRAFT);
            assertThat(form.select("input[name=" + NodeController.NEW_REVISION + "]")).isEmpty();
        }

        @Test
        void aMoveThePersonMayNotMakeIsRefusedAndNothingIsSaved() throws Exception {
            Document form = Jsoup.parse(mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE)
                            .with(editor("create_new_draft")).with(csrf())
                            .param(NodeController.TITLE, "Spring schedule")
                            .param(NodeController.MODERATION_STATE, PUBLISHED))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(form.text()).contains("You may not use the Publish transition.");
            assertThat(queries.query(NodeEntityType.ID).count()).isZero();
        }

        @Test
        void aMoveNoTransitionMakesIsRefused() throws Exception {
            long id = created("Spring schedule", DRAFT);

            Document form = Jsoup.parse(mockMvc.perform(post(NodeEntityType.editPath(id))
                            .with(fullEditor()).with(csrf())
                            .param(NodeController.TITLE, "Spring schedule")
                            .param(NodeController.MODERATION_STATE, ARCHIVED))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(form.text()).contains("There is no transition from draft to archived.");
        }

        @Test
        void aSaveWithoutAStateChosenIsNotSaved() throws Exception {
            mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE).with(fullEditor()).with(csrf())
                            .param(NodeController.TITLE, "Spring schedule"))
                    .andExpect(status().isOk());

            assertThat(queries.query(NodeEntityType.ID).count()).isZero();
        }

        @Test
        void contentOfATypeNoWorkflowModeratesSavesAsBefore() throws Exception {
            Document form = page(NodeController.ADD_PATH + "/" + PAGE, user(new AccountPrincipal(authorId, "edith",
                    "", true, List.of(NodePermissions.create(PAGE)))));

            assertThat(form.select("select[name=" + NodeController.MODERATION_STATE + "]")).isEmpty();
        }
    }

    @Nested
    class TheLatestVersion {

        @Test
        void aRevisionAheadOfTheLiveOneHasItsOwnTabAndPage() throws Exception {
            long id = created("Spring schedule", PUBLISHED);
            edited(id, "Summer schedule", DRAFT);

            assertThat(page(NodeEntityType.path(id), fullEditor()).select("a[href=" + NodeController.latestPath(id)
                    + "]")).isNotEmpty();
            Document latestPage = page(NodeController.latestPath(id), fullEditor());
            assertThat(latestPage.selectFirst("h1").text()).isEqualTo("Summer schedule");
            assertThat(latestPage.selectFirst(".nav-link.active").text()).isEqualTo("Latest version");
        }

        @Test
        void aNodeWithNothingAheadOfItsLiveRevisionHasNoLatestVersion() throws Exception {
            long id = created("Spring schedule", PUBLISHED);

            assertThat(page(NodeEntityType.path(id), fullEditor()).select("a[href=" + NodeController.latestPath(id)
                    + "]")).isEmpty();
            mockMvc.perform(get(NodeController.latestPath(id)).with(fullEditor())).andExpect(status().isNotFound());
        }

        @Test
        void theLatestVersionIsClosedToSomeoneWhoMayNotEditTheNode() throws Exception {
            long id = created("Spring schedule", PUBLISHED);
            edited(id, "Summer schedule", DRAFT);

            mockMvc.perform(get(NodeController.latestPath(id)).with(user(new AccountPrincipal(8L, "other", "", true,
                            List.of(NodePermissions.ACCESS_CONTENT, NodePermissions.VIEW_LATEST_VERSION)))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void theLatestVersionIsClosedToSomeoneWithoutThePermission() throws Exception {
            long id = created("Spring schedule", PUBLISHED);
            edited(id, "Summer schedule", DRAFT);

            mockMvc.perform(get(NodeController.latestPath(id)).with(user(new AccountPrincipal(authorId, "edith", "",
                            true, List.of(NodePermissions.ACCESS_CONTENT, NodePermissions.editOwn(ARTICLE))))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(NodeController.latestPath(999_999)).with(fullEditor()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class TheDashboard {

        @Test
        void theDashboardIsClosedToSomeoneWhoMayNotViewUnpublishedContent() throws Exception {
            mockMvc.perform(get(ModerationController.DASHBOARD_PATH).with(user("visitor")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void publishedContentWithNothingAheadIsLeftOutUntilThatStateIsChosen() throws Exception {
            long published = created("Spring schedule", PUBLISHED);
            long draft = created("Autumn schedule", DRAFT);

            assertThat(page(ModerationController.DASHBOARD_PATH, fullEditor()).select("tbody tr")
                    .eachAttr("data-node")).containsExactly(String.valueOf(draft));
            assertThat(page(ModerationController.DASHBOARD_PATH + "?state=" + PUBLISHED, fullEditor())
                    .select("tbody tr").eachAttr("data-node")).containsExactly(String.valueOf(published));
        }

        @Test
        void eachRowSaysWhoUpdatedItAndOffersEditOnlyToSomeoneWhoMay() throws Exception {
            long id = created("Spring schedule", DRAFT);

            assertThat(page(ModerationController.DASHBOARD_PATH, fullEditor()).selectFirst("tr[data-node=" + id + "]")
                    .text()).contains("Article", "edith");
            assertThat(page(ModerationController.DASHBOARD_PATH, user("reviewer").authorities(
                            new SimpleGrantedAuthority(NodePermissions.VIEW_ANY_UNPUBLISHED)))
                    .select("tr[data-node=" + id + "] a.btn")).isEmpty();
        }

        @Test
        void aRevisionSavedWithoutWhoOrWhenSaysSo() throws Exception {
            EntityData unattributed = entities.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Unattributed",
                    Map.of(NodeEntityType.MODERATION_STATE, DRAFT, BaseFieldDefinition.STATUS, false)));

            assertThat(page(ModerationController.DASHBOARD_PATH, fullEditor())
                    .select("tr[data-node=" + unattributed.id() + "] td").eachText())
                    .containsExactly("Unattributed", "Article", "Draft", "Anonymous");
        }

        @Test
        void anEmptyDashboardSaysSo() throws Exception {
            assertThat(page(ModerationController.DASHBOARD_PATH, fullEditor()).text())
                    .contains("There is no moderated content in this state.");
        }
    }

    @Nested
    class Settings {

        private RequestPostProcessor workflowAdministrator() {
            return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_WORKFLOWS));
        }

        @Test
        void aContentModerationWorkflowLinksToItsSettings() throws Exception {
            assertThat(page(WorkflowController.managePath(EDITORIAL), workflowAdministrator())
                    .selectFirst("a:contains(Moderation settings)").attr("href"))
                    .isEqualTo(ModerationController.settingsPath(EDITORIAL));
        }

        @Test
        void theSettingsShowWhatEachStateMeansAndTheContentTypesModerated() throws Exception {
            Document document = page(ModerationController.settingsPath(EDITORIAL), workflowAdministrator());

            assertThat(document.selectFirst("input[name=" + ModerationController.PUBLISHED_PREFIX + PUBLISHED + "]")
                    .hasAttr("checked")).isTrue();
            assertThat(document.selectFirst("input[name=" + ModerationController.DEFAULT_PREFIX + DRAFT + "]")
                    .hasAttr("checked")).isFalse();
            assertThat(document.selectFirst("input[name=" + ModerationController.BUNDLE_PREFIX + ARTICLE + "]")
                    .hasAttr("checked")).isTrue();
        }

        @Test
        void savingTheSettingsStoresThem() throws Exception {
            mockMvc.perform(post(ModerationController.settingsPath(EDITORIAL)).with(workflowAdministrator()).with(csrf())
                            .param(ModerationController.PUBLISHED_PREFIX + PUBLISHED, "true")
                            .param(ModerationController.DEFAULT_PREFIX + PUBLISHED, "true")
                            .param(ModerationController.DEFAULT_PREFIX + ARCHIVED, "true")
                            .param(ModerationController.BUNDLE_PREFIX + ARTICLE, "true")
                            .param(ModerationController.BUNDLE_PREFIX + PAGE, "true"))
                    .andExpect(redirectedUrl(ModerationController.settingsPath(EDITORIAL)));

            ModerationConfig saved = moderations.config(EDITORIAL).orElseThrow();
            assertThat(saved.bundles()).containsExactly(ARTICLE, PAGE);
            assertThat(saved.settings(ARCHIVED)).isEqualTo(new ModerationStateSettings(false, true));
        }

        @Test
        void aPublishedStateThatIsNotTheDefaultRevisionIsNotSaved() throws Exception {
            Document document = Jsoup.parse(mockMvc.perform(post(ModerationController.settingsPath(EDITORIAL))
                            .with(workflowAdministrator()).with(csrf())
                            .param(ModerationController.PUBLISHED_PREFIX + PUBLISHED, "true"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(document.selectFirst("input[name=" + ModerationController.DEFAULT_PREFIX + PUBLISHED + "]")
                    .hasClass("is-invalid")).isTrue();
            assertThat(moderations.config(EDITORIAL).orElseThrow().bundles()).containsExactly(ARTICLE);
        }

        @Test
        void aContentTypeAnotherWorkflowModeratesCannotBeAdded() throws Exception {
            workflows.save(workflows.create("approval", "Approval", ContentModerationWorkflowType.ID));

            Document form = page(ModerationController.settingsPath("approval"), workflowAdministrator());
            assertThat(form.selectFirst("input[name=" + ModerationController.BUNDLE_PREFIX + ARTICLE + "]")
                    .hasAttr("disabled")).isTrue();

            Document refused = Jsoup.parse(mockMvc.perform(post(ModerationController.settingsPath("approval"))
                            .with(workflowAdministrator()).with(csrf())
                            .param(ModerationController.PUBLISHED_PREFIX + PUBLISHED, "true")
                            .param(ModerationController.DEFAULT_PREFIX + PUBLISHED, "true")
                            .param(ModerationController.BUNDLE_PREFIX + ARTICLE, "true"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(refused.text()).contains("The Editorial workflow already moderates this content type.");
            assertThat(moderations.config("approval")).isEmpty();
        }

        @Test
        void aWorkflowOfAnotherTypeHasNoModerationSettings() throws Exception {
            configStore.save(WorkflowConfig.configName("orphan"), new WorkflowConfig("orphan", "Orphan",
                    "retired_type", List.of(), List.of()));

            mockMvc.perform(get(ModerationController.settingsPath("orphan")).with(workflowAdministrator()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void aWorkflowOfAnotherTypeModeratesNothing() {
            configStore.save(WorkflowConfig.configName("aardvark"), new WorkflowConfig("aardvark", "Aardvark",
                    "retired_type", List.of(), List.of()));

            assertThat(moderations.moderationOf(ARTICLE)).hasValueSatisfying(moderation ->
                    assertThat(moderation.workflow().id()).isEqualTo(EDITORIAL));
        }

        @Test
        void addingTheEditorialWorkflowTwiceKeepsTheFirst() {
            moderations.installEditorial();

            assertThat(moderations.config(EDITORIAL).orElseThrow().bundles()).containsExactly(ARTICLE);
        }

        @Test
        void aStateWithNoSettingsIsAnUnpublishedDraft() {
            assertThat(moderations.config(EDITORIAL).orElseThrow().settings("in_review"))
                    .isEqualTo(ModerationStateSettings.UNPUBLISHED);
        }
    }
}
