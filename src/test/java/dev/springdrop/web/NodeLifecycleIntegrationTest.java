package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.log.DatabaseLog;
import dev.springdrop.kernel.log.LogEntry;
import dev.springdrop.kernel.log.LogFilter;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.menu.NodeMenuLinks;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.moderation.ModerationConfig;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class NodeLifecycleIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final long EDITOR = 5L;

    private static final String CHECKED = FormRenderer.CHECKED_VALUE;

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
    private UserAccountService accounts;

    @Autowired
    private MenuManager menus;

    @Autowired
    private NodeMenuLinks menuLinks;

    @Autowired
    private ContentModerationService moderations;

    @Autowired
    private ConfigStore config;

    @Autowired
    private DatabaseLog log;

    @Autowired
    private ViewManager views;

    @Autowired
    private DefaultViews defaultViews;

    private long ada;

    @BeforeEach
    void anArticleTypeAndAnAuthor() {
        accounts.install();
        menus.install();
        clear();
        ada = accounts.create("ada", "ada@example.com", "").id();
        types.save(NodeType.of(ARTICLE, "Article"));
        views.delete(DefaultViews.CONTENT);
        defaultViews.install();
        log.clear();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
        config.listNames(WorkflowConfig.CONFIG_PREFIX).forEach(config::delete);
        config.listNames(ModerationConfig.CONFIG_PREFIX).forEach(config::delete);
        accounts.findByName("ada").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
    }

    private static RequestPostProcessor account(String... granted) {
        List<String> permissions = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.VIEW_ANY_UNPUBLISHED, NodePermissions.create(ARTICLE),
                NodePermissions.editAny(ARTICLE)));
        permissions.addAll(List.of(granted));
        return user(new AccountPrincipal(EDITOR, "editor", "", true, permissions));
    }

    private static RequestPostProcessor contentAdministrator() {
        return account(NodePermissions.ADMINISTER_NODES, Permissions.ADMINISTER_MENU,
                NodePermissions.deleteAny(ARTICLE));
    }

    private static RequestPostProcessor writer() {
        return account();
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who)).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletResponse submit(String path, RequestPostProcessor who, String... fields)
            throws Exception {
        MockHttpServletRequestBuilder request = post(path);
        for (int index = 0; index < fields.length; index += 2) {
            request.param(fields[index], fields[index + 1]);
        }
        return mockMvc.perform(request.with(csrf()).with(who)).andReturn().getResponse();
    }

    private EntityData onlyNode() {
        return nodes.find(((Number) queries.query(NodeEntityType.ID).ids().getFirst()).longValue()).orElseThrow();
    }

    private List<String> loggedMessages() {
        return log.page(LogFilter.ALL, 0, 10).stream().map(LogEntry::formatted).toList();
    }

    @Test
    void aContentAdministratorSeesThePublishingOptionsInCollapsibleGroups() throws Exception {
        Document form = page(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator());

        assertThat(form.select("details summary").eachText()).contains("Revision information", "URL alias",
                "Authoring information", "Promotion options", "Menu settings");
        assertThat(form.select("input[name=status]")).hasSize(1);
        assertThat(form.selectFirst("input[name=created]").attr("type")).isEqualTo("datetime-local");
        assertThat(form.select("select[name=menu_name] option").eachAttr("value")).contains(MenuConfig.MAIN)
                .doesNotContain(MenuConfig.ADMIN, MenuConfig.ACCOUNT);
        assertThat(form.select("button[name=preview]").eachText()).containsExactly("Preview");
        BootstrapAssertions.assertNoOutlineButtons(form);
    }

    @Test
    void aWriterWithoutThePermissionsSeesNoneOfThemAndCannotSetThem() throws Exception {
        Document form = page(NodeController.ADD_PATH + "/" + ARTICLE, writer());
        assertThat(form.select("input[name=status], input[name=author], input[name=promote], "
                + "input[name=menu_enabled]")).isEmpty();

        submit(NodeController.ADD_PATH + "/" + ARTICLE, writer(), NodeController.TITLE, "Spring schedule",
                NodePublishingOptions.STICKY, CHECKED, NodePublishingOptions.AUTHOR, "ada",
                NodePublishingOptions.MENU, CHECKED, NodePublishingOptions.MENU_NAME, MenuConfig.MAIN,
                NodePublishingOptions.MENU_TITLE, "Spring");

        EntityData node = onlyNode();
        assertThat(node.fields()).containsEntry(NodeEntityType.STICKY, false)
                .containsEntry(BaseFieldDefinition.OWNER, EDITOR);
        assertThat(menuLinks.linkOf(node.id())).isEmpty();
    }

    @Test
    void creatingContentSetsThePublishingOptionsTheMenuLinkAndLogsIt() throws Exception {
        MockHttpServletResponse created = submit(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator(),
                NodeController.TITLE, "Spring schedule",
                NodePublishingOptions.PROMOTE, CHECKED, NodePublishingOptions.STICKY, CHECKED,
                NodePublishingOptions.AUTHOR, "ada", NodePublishingOptions.CREATED, "2026-03-01T09:30",
                NodePublishingOptions.MENU, CHECKED, NodePublishingOptions.MENU_NAME, MenuConfig.MAIN,
                NodePublishingOptions.MENU_TITLE, "Spring", NodePublishingOptions.MENU_WEIGHT, "-3");

        EntityData node = onlyNode();
        assertThat(created.getRedirectedUrl()).isEqualTo(NodeEntityType.path(node.id()));
        assertThat(node.fields()).containsEntry(BaseFieldDefinition.STATUS, false)
                .containsEntry(NodeEntityType.PROMOTE, true)
                .containsEntry(NodeEntityType.STICKY, true)
                .containsEntry(BaseFieldDefinition.OWNER, ada);
        assertThat(((OffsetDateTime) node.fields().get(BaseFieldDefinition.CREATED)).toInstant())
                .isEqualTo(OffsetDateTime.of(2026, 3, 1, 9, 30, 0, 0, ZoneOffset.UTC).toInstant());
        MenuLink link = menuLinks.linkOf(node.id()).orElseThrow();
        assertThat(link.menu()).isEqualTo(MenuConfig.MAIN);
        assertThat(link.title()).isEqualTo("Spring");
        assertThat(link.weight()).isEqualTo(-3);
        assertThat(loggedMessages()).containsExactly("article: added Spring schedule.");
    }

    @Test
    void theEditFormShowsTheOptionsAsTheyStandAndUntickingTheMenuLinkRemovesIt() throws Exception {
        submit(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator(), NodeController.TITLE,
                "Spring schedule", NodePublishingOptions.STATUS, CHECKED, NodePublishingOptions.AUTHOR, "ada",
                NodePublishingOptions.CREATED, "2026-03-01T09:30", NodePublishingOptions.MENU, CHECKED,
                NodePublishingOptions.MENU_NAME, MenuConfig.MAIN, NodePublishingOptions.MENU_TITLE, "Spring");
        EntityData node = onlyNode();

        Document form = page(NodeEntityType.editPath(node.id()), contentAdministrator());
        assertThat(form.selectFirst("input[name=status]").hasAttr("checked")).isTrue();
        assertThat(form.selectFirst("input[name=author]").val()).isEqualTo("ada");
        assertThat(form.selectFirst("input[name=created]").val()).isEqualTo("2026-03-01T09:30");
        assertThat(form.selectFirst("input[name=menu_enabled]").hasAttr("checked")).isTrue();
        assertThat(form.selectFirst("input[name=menu_title]").val()).isEqualTo("Spring");
        assertThat(form.select("a.btn-danger").eachText()).containsExactly("Delete");

        submit(NodeEntityType.editPath(node.id()), contentAdministrator(), NodeController.TITLE, "Spring schedule",
                NodePublishingOptions.AUTHOR, "", NodePublishingOptions.MENU_NAME, MenuConfig.MAIN);

        EntityData saved = onlyNode();
        assertThat(saved.fields()).containsEntry(BaseFieldDefinition.OWNER, UserAccount.ANONYMOUS_ID)
                .containsEntry(BaseFieldDefinition.STATUS, false);
        assertThat(menuLinks.linkOf(node.id())).isEmpty();
        assertThat(loggedMessages()).first().isEqualTo("article: updated Spring schedule.");
        assertThat(page(NodeEntityType.editPath(node.id()), contentAdministrator())
                .selectFirst("input[name=author]").val()).isEmpty();
    }

    @Test
    void aNewNodesMenuLinkTitleStartsAsItsTitle() throws Exception {
        Document form = page(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator());

        assertThat(form.selectFirst("input[name=menu_title]").val()).isEmpty();
        assertThat(form.selectFirst("input[name=menu_weight]").val()).isEqualTo("0");
        assertThat(form.selectFirst("input[name=created]").val()).isEmpty();
    }

    @Test
    void wrongPublishingOptionsComeBackWithTheirErrorsAndNothingIsSaved() throws Exception {
        Document form = Jsoup.parse(submit(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator(),
                NodeController.TITLE, "Spring schedule", NodePublishingOptions.AUTHOR, "nobody",
                NodePublishingOptions.CREATED, "March first", NodePublishingOptions.MENU, CHECKED,
                NodePublishingOptions.MENU_NAME, MenuConfig.ADMIN, NodePublishingOptions.MENU_TITLE, "",
                NodePublishingOptions.MENU_WEIGHT, "heavy").getContentAsString());

        assertThat(form.select(".invalid-feedback").eachText()).contains("There is no account named nobody.",
                "Enter a date and time.", "Choose a menu.", "This value is required.",
                "The weight is a whole number.");
        assertThat(form.selectFirst("input[name=author]").val()).isEqualTo("nobody");
        assertThat(form.selectFirst("input[name=created]").val()).isEqualTo("March first");
        assertThat(form.selectFirst("input[name=menu_weight]").val()).isEqualTo("heavy");
        assertThat(queries.query(NodeEntityType.ID).ids()).isEmpty();
    }

    @Test
    void aMenuLinkWithAnUnreadableWeightIsGivenWeightZero() throws Exception {
        EntityData node = nodes.save(nodes.create(types.find(ARTICLE).orElseThrow(), EDITOR), EDITOR);
        EntityData titled = node.withFields(node.fields());

        new NodePublishingOptions(accounts, menuLinks, menus, null).saveMenuLink(titled, Map.of(
                NodePublishingOptions.MENU, CHECKED, NodePublishingOptions.MENU_NAME, MenuConfig.MAIN,
                NodePublishingOptions.MENU_TITLE, "Spring", NodePublishingOptions.MENU_WEIGHT, "heavy"),
                UsernamePasswordAuthenticationToken.authenticated("admin", null,
                        AuthorityUtils.createAuthorityList(Permissions.ADMINISTER_MENU)));

        assertThat(menuLinks.linkOf(node.id()).orElseThrow().weight()).isZero();
    }

    @Test
    void previewShowsTheContentWithoutSavingIt() throws Exception {
        Document preview = Jsoup.parse(submit(NodeController.ADD_PATH + "/" + ARTICLE, writer(),
                NodeController.TITLE, "Spring schedule", NodeController.PREVIEW, "").getContentAsString());

        assertThat(preview.selectFirst(".node-preview").text()).contains("Spring schedule");
        assertThat(preview.selectFirst("input[name=title]").val()).isEqualTo("Spring schedule");
        assertThat(queries.query(NodeEntityType.ID).ids()).isEmpty();
    }

    @Test
    void deletingAsksFirstThenRemovesTheNodeAndItsMenuLink() throws Exception {
        submit(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator(), NodeController.TITLE,
                "Spring schedule", NodePublishingOptions.MENU, CHECKED, NodePublishingOptions.MENU_NAME, MenuConfig.MAIN,
                NodePublishingOptions.MENU_TITLE, "Spring");
        EntityData node = onlyNode();
        log.clear();

        Document view = page(NodeEntityType.path(node.id()), contentAdministrator());
        assertThat(view.select(".nav-tabs a, .nav a").eachText()).contains("Delete");
        Document confirm = page(NodeController.deletePath(node.id()), contentAdministrator());
        assertThat(confirm.title()).isEqualTo("Delete Spring schedule?");
        assertThat(nodes.find(((Number) node.id()).longValue())).isPresent();

        MockHttpServletResponse deleted = submit(NodeController.deletePath(node.id()), contentAdministrator());

        assertThat(deleted.getRedirectedUrl()).isEqualTo("/");
        assertThat(nodes.find(((Number) node.id()).longValue())).isEmpty();
        assertThat(menuLinks.linkOf(node.id())).isEmpty();
        assertThat(loggedMessages()).containsExactly("article: deleted Spring schedule.");
    }

    @Test
    void someoneWhoMayNotDeleteIsRefusedAndOffered() throws Exception {
        EntityData node = nodes.save(nodes.create(types.find(ARTICLE).orElseThrow(), EDITOR), EDITOR);

        assertThat(mockMvc.perform(get(NodeController.deletePath(node.id())).with(writer())).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(submit(NodeController.deletePath(node.id()), writer()).getStatus()).isEqualTo(403);
        assertThat(page(NodeEntityType.editPath(node.id()), writer()).select("a.btn-danger")).isEmpty();
        assertThat(nodes.find(((Number) node.id()).longValue())).isPresent();
    }

    @Test
    void theContentOverviewFiltersByAuthor() throws Exception {
        submit(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator(), NodeController.TITLE,
                "Spring schedule", NodePublishingOptions.AUTHOR, "ada");
        submit(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator(), NodeController.TITLE,
                "Summer schedule", NodePublishingOptions.AUTHOR, "");
        RequestPostProcessor overviewer = account(DefaultViews.ACCESS_CONTENT_OVERVIEW,
                Permissions.ADMINISTER_USERS);

        Document filtered = page("/admin/content?author=ad", overviewer);

        assertThat(filtered.select("form.views-exposed-form input[name=author]")).hasSize(1);
        assertThat(filtered.select("#view-content-page_1 tbody td:nth-child(2)").eachText())
                .containsExactly("Spring schedule");
    }

    @Test
    void theFirstAccountAndSomeoneWhoBypassesAccessSeeTheOptions() throws Exception {
        RequestPostProcessor first = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true,
                List.of()));

        Document firstForm = page(NodeController.ADD_PATH + "/" + ARTICLE, first);
        assertThat(firstForm.select("input[name=status]")).hasSize(1);
        assertThat(firstForm.select("input[name=menu_enabled]")).hasSize(1);
        assertThat(page(NodeController.ADD_PATH + "/" + ARTICLE, account(NodePermissions.BYPASS_NODE_ACCESS))
                .select("input[name=author]")).hasSize(1);
    }

    @Test
    void aNodeWithoutAnOwnerShowsNoAuthor() throws Exception {
        EntityData node = nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule",
                Map.of(BaseFieldDefinition.STATUS, true)), EDITOR);

        assertThat(page(NodeEntityType.editPath(node.id()), contentAdministrator()).selectFirst("input[name=author]")
                .val()).isEmpty();
    }

    @Test
    void savingContentUnderAWorkflowLeavesItsPublishedStateToTheWorkflow() throws Exception {
        moderations.installEditorial();
        moderations.saveConfig(moderations.config(ContentModerationService.EDITORIAL).orElseThrow()
                .withBundles(List.of(ARTICLE)));

        submit(NodeController.ADD_PATH + "/" + ARTICLE, account(NodePermissions.ADMINISTER_NODES,
                WorkflowManager.transitionPermission(ContentModerationService.EDITORIAL, "create_new_draft")),
                NodeController.TITLE, "Spring schedule",
                NodePublishingOptions.STATUS, CHECKED, NodeController.MODERATION_STATE, "draft",
                NodePublishingOptions.PROMOTE, CHECKED);

        EntityData node = onlyNode();
        assertThat(node.fields()).containsEntry(BaseFieldDefinition.STATUS, false)
                .containsEntry(NodeEntityType.PROMOTE, true);
    }

    @Test
    void contentUnderAWorkflowHasNoPublishedBox() throws Exception {
        moderations.installEditorial();
        moderations.saveConfig(moderations.config(ContentModerationService.EDITORIAL).orElseThrow()
                .withBundles(List.of(ARTICLE)));

        Document form = page(NodeController.ADD_PATH + "/" + ARTICLE, contentAdministrator());

        assertThat(form.select("input[name=status]")).isEmpty();
        assertThat(form.select("input[name=promote]")).hasSize(1);
    }
}
