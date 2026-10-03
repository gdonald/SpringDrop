package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.PagerPlugin;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewEditing;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewOptions;
import dev.springdrop.kernel.views.handlers.BaseValueField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.SomePager;
import dev.springdrop.kernel.views.styles.TableStyle;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
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
class ViewsUiIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "views_article";

    private static final String CONTENT = ViewsUiController.editPath(DefaultViews.CONTENT);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ViewManager views;

    @Autowired
    private DefaultViews defaults;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private MenuLinkContentService menuLinks;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private FieldConfigManager fields;

    @BeforeEach
    void freshDefaultsAndArticles() {
        menuLinks.install();
        accounts.install();
        views.all().forEach(view -> views.delete(view.id()));
        defaults.install();
        nodeTypes.save(NodeType.of(ARTICLE, "Article"));
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(ARTICLE);
        views.all().forEach(view -> views.delete(view.id()));
        defaults.install();
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
    }

    private EntityData article(String title) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BaseFieldDefinition.STATUS, true);
        values.put(NodeEntityType.PROMOTE, true);
        values.put(BaseFieldDefinition.OWNER, 1L);
        values.put(BaseFieldDefinition.CREATED, OffsetDateTime.of(2026, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC));
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, values), 1L);
    }

    private static RequestPostProcessor editor() {
        return user(new AccountPrincipal(5L, "admin", "", true, List.of(DefaultViews.ACCESS_CONTENT_OVERVIEW,
                NodePermissions.ACCESS_CONTENT, NodePermissions.editAny(ARTICLE), "administer user")));
    }

    private static RequestPostProcessor builder() {
        return user(new AccountPrincipal(2L, "builder", "", true, List.of(ViewsUiController.ADMINISTER_VIEWS,
                DefaultViews.ACCESS_CONTENT_OVERVIEW, NodePermissions.ACCESS_CONTENT)));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who)).andReturn().getResponse().getContentAsString());
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields) throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return mockMvc.perform(request.with(builder())).andReturn().getResponse();
    }

    private ViewConfig content() {
        return views.find(DefaultViews.CONTENT).orElseThrow();
    }

    private static String display(String display) {
        return ViewsUiController.displayPath(DefaultViews.CONTENT, display);
    }

    @Test
    void theContentOverviewIsAViewListingContentInASortableTable() throws Exception {
        article("Opening hours");

        Document overview = page("/admin/content", editor());

        assertThat(overview.title()).contains("Content");
        assertThat(overview.select("#view-content-page_1 thead th").eachText()).containsExactly("Title",
                "Content type", "Author", "Status", "Updated", "Operations");
        assertThat(overview.select("#view-content-page_1 tbody tr td:first-child").eachText())
                .containsExactly("Opening hours");
        assertThat(overview.select("#view-content-page_1 tbody tr td:nth-child(4)").eachText())
                .containsExactly("Published");
        assertThat(overview.select("#view-content-page_1 a.btn").eachText()).containsExactly("Edit");
        assertThat(overview.select("form.views-exposed-form input[name=title]")).hasSize(1);
        assertThat(mockMvc.perform(get("/admin/content").with(user("visitor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }

    @Test
    void theContentOverviewsCacheIsClearedWhenListedContentChanges() throws Exception {
        EntityData first = article("Opening hours");
        assertThat(page("/admin/content", editor()).select("#view-content-page_1 tbody tr")).hasSize(1);

        article("Closing hours");
        nodes.save(first.withFields(new LinkedHashMap<>(first.fields())), 1L);

        assertThat(page("/admin/content", editor()).select("#view-content-page_1 tbody tr td:first-child")
                .eachText()).containsExactlyInAnyOrder("Opening hours", "Closing hours");
    }

    @Test
    void theContentOverviewIsEditedThroughTheViewsUi() throws Exception {
        article("Opening hours");
        Document edit = page(CONTENT, builder());
        assertThat(edit.select("[data-view-display-tab]").eachText()).containsExactly("Default", "Content");
        assertThat(edit.select("[data-view-part=fields] li span").eachText()).contains("Label label");

        MockHttpServletResponse added = submit(display(ViewDisplay.DEFAULT) + "/fields/add",
                Map.of(ViewsUiController.PLUGIN, BaseValueField.ID, ViewsUiController.TARGET, "none|id"));
        assertThat(added.getRedirectedUrl()).isEqualTo(display(ViewDisplay.DEFAULT) + "/fields/id");
        assertThat(page(display(ViewDisplay.DEFAULT) + "/fields/id", builder()).select("input[name=settings_label]"))
                .hasSize(1);
        submit(display(ViewDisplay.DEFAULT) + "/fields/id", Map.of("settings_label", "Number"));

        assertThat(page("/admin/content", editor()).select("#view-content-page_1 thead th").eachText())
                .contains("Number");
        assertThat(content().options(ViewDisplay.DEFAULT).fields()).extracting(HandlerConfig::id).contains("id");
    }

    @Test
    void aHandlerIsRemovedAndAnotherOfTheSamePropertyGetsItsOwnId() throws Exception {
        submit(display(ViewDisplay.DEFAULT) + "/fields/add", Map.of(ViewsUiController.PLUGIN, BaseValueField.ID,
                ViewsUiController.TARGET, "none|label"));
        assertThat(content().options(ViewDisplay.DEFAULT).fields()).extracting(HandlerConfig::id).contains("label");
        submit(display(ViewDisplay.DEFAULT) + "/fields/add", Map.of(ViewsUiController.PLUGIN, BaseValueField.ID,
                ViewsUiController.TARGET, "none|label"));
        assertThat(content().options(ViewDisplay.DEFAULT).fields()).extracting(HandlerConfig::id).contains("label_2");

        submit(display(ViewDisplay.DEFAULT) + "/fields/label_2/remove", Map.of());

        assertThat(content().options(ViewDisplay.DEFAULT).fields()).extracting(HandlerConfig::id)
                .doesNotContain("label_2");
    }

    @Test
    void aHandlerReadsThePropertiesOfARelationshipsEntity() throws Exception {
        Document form = page(display(ViewDisplay.DEFAULT) + "/filters/add", builder());

        assertThat(form.select("select[name=target] option").eachAttr("value")).contains("none|label",
                "none|status", "author|label", "author|mail");
        assertThat(page(display(ViewDisplay.DEFAULT) + "/relationships/add", builder())
                .select("select[name=target] option").eachAttr("value")).doesNotContain("author|label");
    }

    @Test
    void anAddedHandlerNeedsAPluginAndAPropertyTheViewHas() throws Exception {
        String wrongPlugin = submit(display(ViewDisplay.DEFAULT) + "/fields/add", Map.of(ViewsUiController.PLUGIN,
                "retired", ViewsUiController.TARGET, "none|id")).getContentAsString();
        String wrongTarget = submit(display(ViewDisplay.DEFAULT) + "/fields/add", Map.of(ViewsUiController.PLUGIN,
                BaseValueField.ID, ViewsUiController.TARGET, "none|nothing")).getContentAsString();
        String missing = submit(display(ViewDisplay.DEFAULT) + "/fields/add", Map.of()).getContentAsString();

        assertThat(wrongPlugin).contains("Choose what handles it.");
        assertThat(wrongTarget).contains("Choose a property.");
        assertThat(missing).contains("This value is required.");
    }

    @Test
    void aPluginIsChangedAndThenConfigured() throws Exception {
        assertThat(page(display(ViewDisplay.DEFAULT) + "/plugin/pager", builder()).select("input[name=settings_items_per_page]"))
                .hasSize(1);

        MockHttpServletResponse switched = submit(display(ViewDisplay.DEFAULT) + "/plugin/pager",
                Map.of(ViewsUiController.PLUGIN, SomePager.ID));
        assertThat(switched.getRedirectedUrl()).isEqualTo(display(ViewDisplay.DEFAULT) + "/plugin/pager");
        submit(display(ViewDisplay.DEFAULT) + "/plugin/pager", Map.of(ViewsUiController.PLUGIN, SomePager.ID,
                "settings_items_per_page", "3", "settings_offset", "0"));

        assertThat(content().options(ViewDisplay.DEFAULT).pager())
                .isEqualTo(new PluginConfig(SomePager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 3, PagerPlugin.OFFSET, 0)));
        assertThat(submit(display(ViewDisplay.DEFAULT) + "/plugin/pager", Map.of(ViewsUiController.PLUGIN,
                SomePager.ID, "settings_items_per_page", "many")).getContentAsString()).contains("Use a whole number.");
        assertThat(submit(display(ViewDisplay.DEFAULT) + "/plugin/pager", Map.of(ViewsUiController.PLUGIN,
                "retired")).getContentAsString()).contains("Choose a plugin.");
    }

    @Test
    void aDisplayTakesItsOwnCopyOfAPartAndGivesItBack() throws Exception {
        String toggle = display("page_1") + "/override/style";
        assertThat(page(CONTENT + "?display=page_1", builder()).select("form[action=" + toggle + "] button").text())
                .isEqualTo("Change for this display only");

        submit(toggle, Map.of());
        assertThat(ViewEditing.overrides(content(), "page_1", ViewEditing.STYLE)).isTrue();
        submit(display("page_1") + "/plugin/style", Map.of(ViewsUiController.PLUGIN, TableStyle.ID,
                "settings_sortable", ""));
        assertThat(content().display("page_1").orElseThrow().overrides().style().settings())
                .containsEntry(TableStyle.SORTABLE, false);
        assertThat(content().defaultDisplay().overrides().style().settings()).containsEntry(TableStyle.SORTABLE, true);

        submit(toggle, Map.of());
        submit(display("page_1") + "/override/fields", Map.of());
        submit(display("page_1") + "/override/fields", Map.of());
        submit(display(ViewDisplay.DEFAULT) + "/override/fields", Map.of());
        submit(display("page_1") + "/override/nothing", Map.of());

        assertThat(content().display("page_1").orElseThrow().overrides()).isEqualTo(ViewOptions.INHERIT);
    }

    @Test
    void aDisplaysSettingsAreSavedAndAPathTheSiteServesIsRefused() throws Exception {
        submit(display("page_1") + "/settings", Map.of(ViewsUiController.TITLE, "All content", ViewDisplay.PATH,
                "/admin/all-content", ViewDisplay.MENU_TITLE, "All content", ViewDisplay.MENU, "admin",
                ViewDisplay.EXPOSED_BLOCK, "true", "empty_text", "Nothing here."));
        ViewDisplay saved = content().display("page_1").orElseThrow();
        assertThat(saved.title()).isEqualTo("All content");
        assertThat(saved.settings()).containsEntry(ViewDisplay.PATH, "/admin/all-content")
                .containsEntry(ViewDisplay.EXPOSED_BLOCK, true).containsEntry(ViewDisplay.MENU, "admin");
        assertThat(page("/admin/all-content", editor()).select("#view-content-page_1")).hasSize(1);

        String served = submit(display("page_1") + "/settings", Map.of(ViewDisplay.PATH, "/user/login"))
                .getContentAsString();
        String wrong = submit(display("page_1") + "/settings", Map.of(ViewDisplay.PATH, "content"))
                .getContentAsString();
        assertThat(served).contains("The path /user/login is already a page of the site.");
        assertThat(wrong).contains("Write the path as /news or /news/%");

        submit(display(ViewDisplay.DEFAULT) + "/settings", Map.of(ViewsUiController.TITLE, "Content"));
        assertThat(content().defaultDisplay().settings()).containsEntry(ViewDisplay.ENTITY_ACCESS, false);
    }

    @Test
    void displaysAreAddedAndDeleted() throws Exception {
        submit(CONTENT + "/displays/add", Map.of(ViewsUiController.PLUGIN, ViewDisplay.BLOCK));
        submit(CONTENT + "/displays/add", Map.of(ViewsUiController.PLUGIN, ViewDisplay.PAGE));
        submit(CONTENT + "/displays/add", Map.of(ViewsUiController.PLUGIN, ViewDisplay.FEED));
        submit(CONTENT + "/displays/add", Map.of(ViewsUiController.PLUGIN, ViewDisplay.FEED));
        submit(CONTENT + "/displays/add", Map.of(ViewsUiController.PLUGIN, "table"));

        assertThat(content().displays()).extracting(ViewDisplay::id).containsExactly(ViewDisplay.DEFAULT, "page_1",
                "block_1", "page_2", "feed_1", "feed_2");
        assertThat(content().display("page_2").orElseThrow().settings()).containsEntry(ViewDisplay.PATH,
                "/content-2");
        assertThat(content().display("feed_2").orElseThrow().settings()).containsEntry(ViewDisplay.PATH,
                "/content/feed-2");
        assertThat(page(CONTENT + "?display=block_1", builder()).select("input[name=path]")).isEmpty();

        submit(display("block_1") + "/delete", Map.of());
        submit(display(ViewDisplay.DEFAULT) + "/delete", Map.of());
        assertThat(content().displays()).extracting(ViewDisplay::id).doesNotContain("block_1")
                .contains(ViewDisplay.DEFAULT);
    }

    @Test
    void viewsAreListedAddedAndDeleted() throws Exception {
        Document list = page(ViewsUiController.PATH, builder());
        assertThat(list.select("tr[data-view]").eachAttr("data-view")).containsExactlyInAnyOrder(
                DefaultViews.CONTENT, DefaultViews.FILES, DefaultViews.FRONTPAGE, DefaultViews.TAXONOMY_TERM,
                DefaultViews.PEOPLE);
        assertThat(list.selectFirst("tr[data-view=content]").text()).contains("Content (/admin/content)");
        assertThat(list.selectFirst("tr[data-view=frontpage]").text()).contains("Front page feed (/rss.xml)");

        MockHttpServletResponse added = submit(ViewsUiController.PATH + "/add", Map.of(ViewsUiController.LABEL,
                "Latest articles", ViewsUiController.BASE, NodeEntityType.ID, ViewDisplay.PATH, "/latest"));
        assertThat(added.getRedirectedUrl()).isEqualTo(ViewsUiController.editPath("latest_articles"));
        assertThat(views.find("latest_articles").orElseThrow().display("page_1")).isPresent();
        submit(ViewsUiController.PATH + "/add", Map.of(ViewsUiController.LABEL, "Blocks only",
                ViewsUiController.BASE, NodeEntityType.ID));
        assertThat(views.find("blocks_only").orElseThrow().displays()).hasSize(1);
        assertThat(page(ViewsUiController.PATH, builder()).selectFirst("tr[data-view=blocks_only]").text())
                .doesNotContain("(");
        assertThat(page(ViewsUiController.PATH + "/add", builder()).select("select[name=base] option").eachAttr(
                "value")).contains(NodeEntityType.ID, "user").doesNotContain("node_type");

        assertThat(page(ViewsUiController.editPath("latest_articles") + "/delete", builder()).text())
                .contains("Delete the view Latest articles?");
        submit(ViewsUiController.editPath("latest_articles") + "/delete", Map.of());
        assertThat(views.find("latest_articles")).isEmpty();
    }

    @Test
    void aViewNeedsANameAThingToListAndAFreePath() throws Exception {
        String refused = submit(ViewsUiController.PATH + "/add", Map.of(ViewsUiController.LABEL, "",
                ViewsUiController.BASE, "node_type", ViewDisplay.PATH, "/user/login")).getContentAsString();
        String unchosen = submit(ViewsUiController.PATH + "/add", Map.of(ViewsUiController.LABEL, "x",
                ViewsUiController.BASE, "", ViewDisplay.PATH, "/news/%")).getContentAsString();

        assertThat(refused).contains("This value is required.").contains("Choose what the view lists.")
                .contains("The path /user/login is already a page of the site.");
        assertThat(unchosen).contains("This value is required.").doesNotContain("Choose what the view lists.");
    }

    @Test
    void handlerAndPluginSettingsFormsOfferEachPluginsSettings() throws Exception {
        Document filter = page(display(ViewDisplay.DEFAULT) + "/filters/status", builder());
        Document sort = page(display(ViewDisplay.DEFAULT) + "/sorts/changed", builder());
        Document relationship = page(display(ViewDisplay.DEFAULT) + "/relationships/author", builder());
        Document style = page(display(ViewDisplay.DEFAULT) + "/plugin/style", builder());
        Document access = page(display(ViewDisplay.DEFAULT) + "/plugin/access", builder());

        assertThat(filter.select("select[name=settings_operator] option").eachAttr("value")).contains("contains");
        assertThat(filter.selectFirst("input[name=settings_exposed]").hasAttr("checked")).isTrue();
        assertThat(sort.select("select[name=settings_order] option[selected]").val()).isEqualTo("desc");
        assertThat(relationship.select("input[name=settings_target_type]")).hasSize(1);
        assertThat(style.selectFirst("input[name=settings_sortable]").hasAttr("checked")).isTrue();
        assertThat(access.selectFirst("input[name=settings_permission]").val())
                .isEqualTo(DefaultViews.ACCESS_CONTENT_OVERVIEW);
        assertThat(page(ViewsUiController.editPath(DefaultViews.TAXONOMY_TERM) + "/display/default/arguments/term",
                builder()).select("select[name=settings_default_action] option")).hasSize(3);
    }

    @Test
    void theEditPageMarksMissingPluginsAndPartsWithNothing() throws Exception {
        views.save(new ViewConfig("odd", "Odd", "", NodeEntityType.ID, List.of(new ViewDisplay(ViewDisplay.DEFAULT,
                ViewDisplay.DEFAULT, "", new ViewOptions(List.of(HandlerConfig.of("x",
                "retired", "label", Map.of()).through("author")), null, null, null, List.of(HandlerConfig.of(
                "author", "retired", "owner", Map.of())), PluginConfig.of("retired"), null, null, null), Map.of()))));

        Document edit = page(ViewsUiController.editPath("odd"), builder());

        assertThat(edit.select("[data-view-part=fields] li span").eachText()).containsExactly(
                "Missing retired author: label");
        assertThat(edit.select("[data-view-part=pager] li span").eachText()).containsExactly("Missing retired");
        assertThat(edit.select("[data-view-part=style] li span").eachText()).containsExactly("None");
        assertThat(page(ViewsUiController.editPath("odd") + "/display/default/fields/x", builder())
                .select("input[name^=settings_]")).isEmpty();
        assertThat(submit(ViewsUiController.editPath("odd") + "/display/default/fields/x", Map.of())
                .getRedirectedUrl()).isEqualTo(ViewsUiController.editPath("odd") + "?display=default");
        assertThat(page(ViewsUiController.editPath("odd") + "/display/default/plugin/style", builder())
                .select("select[name=plugin] option[selected]")).isEmpty();
        assertThat(page(ViewsUiController.editPath("odd") + "/display/default/fields/add", builder())
                .select("select[name=target] option").eachAttr("value")).doesNotContain("author|label");
    }

    @Test
    void aViewDisplayOrPartTheSiteLacksIsNotFound() throws Exception {
        for (String path : List.of(ViewsUiController.editPath("retired"), CONTENT + "?display=retired",
                display(ViewDisplay.DEFAULT) + "/nothing/add", display(ViewDisplay.DEFAULT) + "/fields/missing",
                display(ViewDisplay.DEFAULT) + "/plugin/nothing")) {
            assertThat(mockMvc.perform(get(path).with(builder())).andReturn().getResponse().getStatus())
                    .as(path).isEqualTo(404);
        }
        assertThat(mockMvc.perform(get(ViewsUiController.PATH).with(editor())).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }

    @Test
    void theFilesOverviewListsManagedFiles() throws Exception {
        files.store(new byte[12], "annual.pdf", FileSchemes.PUBLIC, 1L);

        Document overview = page("/admin/content/files", user("admin").authorities(
                new SimpleGrantedAuthority(
                        DefaultViews.ACCESS_FILES_OVERVIEW)));

        assertThat(overview.select("#view-files-page_1 tbody tr td").eachText()).contains("annual.pdf",
                "application/pdf", "12", "Temporary");
    }

    @Test
    void theFrontPageFeedListsPromotedContent() throws Exception {
        article("Opening hours");

        Document feed = Jsoup.parse(mockMvc.perform(get("/rss.xml").with(editor())).andReturn().getResponse()
                .getContentAsString(), "", Parser.xmlParser());

        assertThat(feed.select("item > title").eachText()).containsExactly("Opening hours");
    }

    @Test
    void aDefaultViewAlreadyThereIsKept() {
        ViewConfig changed = new ViewConfig(DefaultViews.CONTENT, "My content", "", NodeEntityType.ID,
                content().displays());
        views.save(changed);

        defaults.install();

        assertThat(content().label()).isEqualTo("My content");
        assertThat(content().options("page_1").pager())
                .isEqualTo(new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 50)));
    }

    @Test
    void untitledDisplaysAreNamedByTheirIdsAndFeedsKeepAPath() throws Exception {
        views.save(new ViewConfig("bare", "Bare", "", NodeEntityType.ID, List.of(
                new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", ViewOptions.INHERIT, Map.of()),
                new ViewDisplay("side", ViewDisplay.BLOCK, "", ViewOptions.INHERIT, Map.of()),
                new ViewDisplay("feed_1", ViewDisplay.FEED, "Feed", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/bare/feed")))));
        String feed = ViewsUiController.displayPath("bare", "feed_1");

        assertThat(page(ViewsUiController.PATH, builder()).selectFirst("tr[data-view=bare]").text())
                .contains("side (block)");
        assertThat(page(ViewsUiController.editPath("bare"), builder()).select("[data-view-display-tab]")
                .eachText()).containsExactly("Default", "side", "Feed");
        assertThat(page(ViewsUiController.editPath("bare") + "?display=feed_1", builder())
                .selectFirst("input[name=path]").val()).isEqualTo("/bare/feed");
        submit(feed + "/settings", Map.of(ViewsUiController.TITLE, "Bare feed", ViewDisplay.PATH, "/bare/rss"));
        assertThat(views.find("bare").orElseThrow().display("feed_1").orElseThrow().settings())
                .containsEntry(ViewDisplay.PATH, "/bare/rss").doesNotContainKey(ViewDisplay.MENU);

        submit(ViewsUiController.editPath("bare") + "/displays/add", Map.of(ViewsUiController.PLUGIN,
                ViewDisplay.PAGE));
        assertThat(views.find("bare").orElseThrow().display("page_1").orElseThrow().settings())
                .containsEntry(ViewDisplay.PATH, "/bare");
    }

    @Test
    void aViewPathWithoutALeadingSlashIsRefused() throws Exception {
        assertThat(submit(ViewsUiController.PATH + "/add", Map.of(ViewsUiController.LABEL, "News",
                ViewsUiController.BASE, NodeEntityType.ID, ViewDisplay.PATH, "news")).getContentAsString())
                .contains("Write the path as /news or /news/%");
    }

    @Test
    void aHandlerSettingTooLongIsRefusedOnTheForm() throws Exception {
        MockHttpServletResponse refused = submit(display(ViewDisplay.DEFAULT) + "/filters/status",
                Map.of("settings_label", "x".repeat(256)));

        assertThat(refused.getRedirectedUrl()).isNull();
        assertThat(Jsoup.parse(refused.getContentAsString()).select("input[name=settings_label]")).hasSize(1);
        assertThat(content().options(ViewDisplay.DEFAULT).filters().stream().filter(each -> each.id().equals(
                "status")).findFirst().orElseThrow().text("label")).hasSizeLessThan(256);
    }

    @Test
    void aHandlerOfAPropertyNamedAddIsGivenAnotherId() throws Exception {
        fields.createStorage(FieldStorageConfig.single("add", NodeEntityType.ID, StringFieldType.ID));
        try {
            MockHttpServletResponse added = submit(display(ViewDisplay.DEFAULT) + "/fields/add", Map.of(
                    ViewsUiController.PLUGIN, BaseValueField.ID, ViewsUiController.TARGET, "none|add"));

            assertThat(added.getRedirectedUrl()).isEqualTo(display(ViewDisplay.DEFAULT) + "/fields/add_2");
        } finally {
            fields.deleteStorage(NodeEntityType.ID, "add");
        }
    }

    @Test
    void aViewWithoutRelationshipsOrFiltersOffersItsOwnPropertiesAndFindsNoFilter() throws Exception {
        views.save(new ViewConfig("plain", "Plain", "", NodeEntityType.ID, List.of(new ViewDisplay(
                ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", ViewOptions.INHERIT, Map.of()))));
        String plain = ViewsUiController.displayPath("plain", ViewDisplay.DEFAULT);

        assertThat(page(plain + "/fields/add", builder()).select("select[name=target] option").eachAttr("value"))
                .contains("none|label").noneMatch(value -> value.startsWith("author|"));
        assertThat(mockMvc.perform(get(plain + "/filters/status").with(builder())).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(display("retired") + "/filters/status").with(builder())).andReturn()
                .getResponse().getStatus()).isEqualTo(404);
    }
}
