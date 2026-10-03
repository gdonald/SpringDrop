package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuNavigation;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.Link;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.FilterHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.PagerPlugin;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.SortHandler;
import dev.springdrop.kernel.views.ViewCache;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewExecutor;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewMenuLinks;
import dev.springdrop.kernel.views.ViewOptions;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.views.blocks.ExposedFormBlockDeriver;
import dev.springdrop.kernel.views.blocks.ViewBlockDeriver;
import dev.springdrop.kernel.views.handlers.BaseValueField;
import dev.springdrop.kernel.views.handlers.EntityLabelField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.MiniPager;
import dev.springdrop.kernel.views.handlers.NoneAccess;
import dev.springdrop.kernel.views.handlers.OperationsField;
import dev.springdrop.kernel.views.handlers.PermissionAccess;
import dev.springdrop.kernel.views.handlers.SomePager;
import dev.springdrop.kernel.views.handlers.StandardSort;
import dev.springdrop.kernel.views.handlers.ValueArgument;
import dev.springdrop.kernel.views.handlers.ValueFilter;
import dev.springdrop.kernel.views.rows.EntityRow;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.GridStyle;
import dev.springdrop.kernel.views.styles.HtmlListStyle;
import dev.springdrop.kernel.views.styles.TableStyle;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ViewDisplaysIntegrationTest extends AbstractIntegrationTest {

    private static final String STORY = "display_story";

    private static final String VIEW = "story_list";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ViewManager views;

    @Autowired
    private ViewCache cache;

    @Autowired
    private ViewRenderer renderer;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private PluginRegistry registry;

    @Autowired
    private RenderService renderService;

    @Autowired
    private MenuLinkContentService menuLinks;

    @Autowired
    private MenuNavigation navigation;

    @Autowired
    private ViewMenuLinks viewMenuLinks;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private UserAccountService accounts;

    @BeforeEach
    void storiesAndAView() {
        menuLinks.install();
        nodeTypes.save(NodeType.of(STORY, "Story"));
        int day = 1;
        for (String title : List.of("Bridges", "Apples", "Canals", "Docks", "Elms")) {
            story(title, day++, true);
        }
        story("Hidden", 9, false);
        views.save(storyView());
    }

    @AfterEach
    void removeEverything() {
        SecurityContextHolder.clearContext();
        views.all().forEach(view -> views.delete(view.id()));
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(STORY);
    }

    private EntityData story(String title, int day, boolean published) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BaseFieldDefinition.STATUS, published);
        values.put(BaseFieldDefinition.OWNER, 1L);
        values.put(BaseFieldDefinition.CREATED, OffsetDateTime.of(2026, 1, day, 9, 0, 0, 0, ZoneOffset.UTC));
        return nodes.save(EntityData.of(NodeEntityType.ID, null, STORY, title, values), 1L);
    }

    private static HandlerConfig titleField() {
        return HandlerConfig.of("title", EntityLabelField.ID, "label", Map.of("label", "Title"));
    }

    private static ViewConfig storyView() {
        ViewOptions defaults = new ViewOptions(
                List.of(titleField(), HandlerConfig.of("created", BaseValueField.ID, BaseFieldDefinition.CREATED,
                        Map.of("label", "Created")), HandlerConfig.of("edit", OperationsField.ID, "id", Map.of())),
                List.of(HandlerConfig.of("status", ValueFilter.ID, BaseFieldDefinition.STATUS,
                        Map.of(FilterHandler.VALUE, "true", "type", "boolean")),
                        HandlerConfig.of("type", ValueFilter.ID, NodeEntityType.BUNDLE_KEY,
                                Map.of(FilterHandler.VALUE, STORY)),
                        HandlerConfig.of("title", ValueFilter.ID, "label", Map.of(FilterHandler.EXPOSED, true,
                                FilterHandler.IDENTIFIER, "title", ValueFilter.OPERATOR, "contains", "label", "Title"))),
                List.of(HandlerConfig.of("title", StandardSort.ID, "label", Map.of())),
                List.of(), List.of(),
                new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 2)),
                new PluginConfig(TableStyle.ID, Map.of()), PluginConfig.of(FieldsRow.ID),
                new PluginConfig(PermissionAccess.ID, Map.of(PermissionAccess.PERMISSION,
                        NodePermissions.ACCESS_CONTENT)));
        ViewOptions teasers = ViewOptions.INHERIT
                .withStyle(new PluginConfig(HtmlListStyle.ID, Map.of(HtmlListStyle.TYPE, "ol")))
                .withRow(new PluginConfig(EntityRow.ID, Map.of(EntityRow.VIEW_MODE, "teaser")))
                .withPager(new PluginConfig(MiniPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 3)));
        ViewOptions newest = ViewOptions.INHERIT
                .withSorts(List.of(HandlerConfig.of("created", StandardSort.ID, BaseFieldDefinition.CREATED,
                        Map.of(SortHandler.ORDER, "desc"))))
                .withPager(new PluginConfig(SomePager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 2)))
                .withStyle(new PluginConfig(GridStyle.ID, Map.of(GridStyle.COLUMNS, 2)))
                .withRow(new PluginConfig(FieldsRow.ID, Map.of(FieldsRow.LABELS, true)));
        return new ViewConfig(VIEW, "Stories", "Published stories.", NodeEntityType.ID, List.of(
                new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "Stories", defaults, Map.of()),
                new ViewDisplay("table", ViewDisplay.PAGE, "Story table", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/stories/table", ViewDisplay.MENU_TITLE, "Story table",
                                ViewRenderer.EMPTY_TEXT, "No stories match.")),
                new ViewDisplay("teasers", ViewDisplay.PAGE, "Stories", teasers,
                        Map.of(ViewDisplay.PATH, "/stories", ViewDisplay.EXPOSED_BLOCK, true)),
                new ViewDisplay("newest", ViewDisplay.BLOCK, "Newest stories", newest, Map.of()),
                new ViewDisplay("feed", ViewDisplay.FEED, "Story feed", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/stories/feed"))));
    }

    private static RequestPostProcessor reader() {
        return user("reader").authorities(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(reader())).andReturn().getResponse().getContentAsString());
    }

    private static List<String> rowTitles(Element view) {
        return view.select("tbody tr td:first-child").eachText();
    }

    @Test
    void aViewRendersAsAPagedSortableBootstrapTable() throws Exception {
        Document first = page("/stories/table");
        Element view = first.selectFirst("#view-" + VIEW + "-table");

        assertThat(first.title()).contains("Story table");
        assertThat(view.selectFirst("table").hasClass("table")).isTrue();
        assertThat(view.select("thead th").eachText()).containsExactly("Title", "Created", "Operations");
        assertThat(rowTitles(view)).containsExactly("Apples", "Bridges");
        assertThat(view.select("ul.pagination a.page-link").eachText()).containsExactly("Previous", "1", "2", "3",
                "Next");
        String sortByCreated = view.selectFirst("thead th:nth-child(2) a").attr("href");
        assertThat(sortByCreated).isEqualTo("/stories/table?order=created&sort=asc");
        assertThat(view.select("thead th:nth-child(3) a")).isEmpty();

        Element descending = page("/stories/table?order=created&sort=desc").selectFirst("#view-" + VIEW + "-table");
        assertThat(rowTitles(descending)).containsExactly("Elms", "Docks");
        assertThat(descending.selectFirst("thead th:nth-child(2) a").attr("href"))
                .isEqualTo("/stories/table?order=created&sort=asc");
        assertThat(page("/stories/table?order=created&sort=asc").selectFirst("thead th:nth-child(2) a").attr("href"))
                .isEqualTo("/stories/table?order=created&sort=desc");
        assertThat(rowTitles(page("/stories/table?page=3").selectFirst("#view-" + VIEW + "-table")))
                .containsExactly("Elms");
    }

    @Test
    void aViewRendersAsAListOfTeasersWithAWorkingExposedFilter() throws Exception {
        Document listing = page("/stories?title=s");
        Element view = listing.selectFirst("#view-" + VIEW + "-teasers");

        assertThat(view.selectFirst("ol.views-list")).isNotNull();
        assertThat(view.select("ol.views-list > li article.node h2 a").eachText())
                .containsExactly("Apples", "Bridges", "Canals");
        assertThat(view.select("ul.pagination a.page-link").eachText()).containsExactly("Previous", "Next");
        assertThat(view.selectFirst("ul.pagination li:last-child a").attr("href")).isEqualTo("/stories?title=s&page=2");
        assertThat(view.select("form.views-exposed-form")).isEmpty();
        assertThat(page("/stories?title=s&page=2").select("#view-" + VIEW + "-teasers article.node h2").eachText())
                .containsExactly("Docks", "Elms");
    }

    @Test
    void theExposedFormIsDrawnAboveTheResultsAndReplacesThemThroughHtmx() throws Exception {
        Element form = page("/stories/table?title=nal").selectFirst("form.views-exposed-form");

        assertThat(form.attr("action")).isEqualTo("/stories/table");
        assertThat(form.attr("hx-get")).isEqualTo("/stories/table");
        assertThat(form.attr("hx-target")).isEqualTo("#view-" + VIEW + "-table");
        assertThat(form.attr("hx-select")).isEqualTo("#view-" + VIEW + "-table");
        assertThat(form.selectFirst("input[name=title]").val()).isEqualTo("nal");
        assertThat(rowTitles(page("/stories/table?title=nal").selectFirst("#view-" + VIEW + "-table")))
                .containsExactly("Canals");
        assertThat(page("/stories/table?title=zzz").selectFirst(".views-empty").text()).isEqualTo("No stories match.");
    }

    @Test
    void onePageAndOneBlockDisplayShareTheQueryWithPerDisplayOverrides() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null,
                List.of(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT))));
        BlockPlugin block = registry.managerFor(BlockPlugin.class).get(ViewBlockDeriver.ID + ":" + VIEW + "-newest");

        Renderable built = block.build(BlockContext.of("/", "Home"), Map.of()).orElseThrow();
        Document drawn = Jsoup.parseBodyFragment(renderService.render(built).html());

        assertThat(block.label()).isEqualTo("Stories: Newest stories");
        assertThat(drawn.select(".views-grid .views-row .views-field-title a").eachText())
                .containsExactly("Elms", "Docks");
        assertThat(drawn.select(".views-grid .views-label").first().text()).isEqualTo("Title:");
        assertThat(drawn.select("ul.pagination")).isEmpty();
        assertThat(block.cacheability(Map.of()).isCacheable()).isFalse();
    }

    @Test
    void aPageDisplaysExposedFormIsABlockOfItsOwn() {
        BlockPlugin block = registry.managerFor(BlockPlugin.class).get(ExposedFormBlockDeriver.ID + ":" + VIEW
                + "-teasers");

        Document form = Jsoup.parseBodyFragment(renderService.render(block.build(BlockContext.of("/", "Home"),
                Map.of()).orElseThrow()).html());

        assertThat(block.label()).isEqualTo("Exposed form: Stories: Stories");
        assertThat(form.selectFirst("form").attr("action")).isEqualTo("/stories");
        assertThat(form.selectFirst("form").attr("hx-target")).isEqualTo("#view-" + VIEW + "-teasers");
        assertThat(block.cacheability(Map.of()).isCacheable()).isFalse();
        assertThat(registry.managerFor(BlockPlugin.class).has(ExposedFormBlockDeriver.ID + ":" + VIEW + "-table"))
                .isFalse();
    }

    @Test
    void aFeedDisplayServesRss() throws Exception {
        MockHttpServletResponse feed = mockMvc.perform(get("/stories/feed").with(reader())).andReturn().getResponse();
        Document rss = Jsoup.parse(feed.getContentAsString(), "", org.jsoup.parser.Parser.xmlParser());

        assertThat(feed.getContentType()).startsWith("application/rss+xml");
        assertThat(rss.select("channel > title").text()).isEqualTo("Story feed");
        assertThat(rss.select("item > title").eachText()).containsExactly("Apples", "Bridges");
        assertThat(rss.select("item > pubDate").first().text()).isEqualTo("Fri, 2 Jan 2026 09:00:00 GMT");
    }

    @Test
    void aPageDisplayWithAMenuTitleAddsAMenuLink() {
        List<Link> menu = navigation.of(MenuConfig.MAIN, "/");

        assertThat(menu).extracting(Link::label, Link::url).contains(
                org.assertj.core.groups.Tuple.tuple("Story table", "/stories/table"));
    }

    @Test
    void aPageDisplayIsRefusedToSomeoneTheAccessPluginTurnsAway() throws Exception {
        assertThat(mockMvc.perform(get("/stories/table").with(user("visitor"))).andReturn().getResponse().getStatus())
                .isEqualTo(403);
        assertThat(mockMvc.perform(get("/views/page/" + VIEW + "/newest").with(reader())).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get("/views/page/retired/page").with(reader())).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
        assertThat(page("/views/page/" + VIEW + "/table").select("#view-" + VIEW + "-table tbody tr")).hasSize(2);
    }

    @Test
    void aBlockTheReaderMayNotSeeOrWithNothingToShowDrawsNothing() {
        BlockPlugin block = registry.managerFor(BlockPlugin.class).get(ViewBlockDeriver.ID + ":" + VIEW + "-newest");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("visitor", null,
                List.of()));
        assertThat(block.build(BlockContext.of("/", "Home"), Map.of())).isEmpty();

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null,
                List.of(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT))));
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        assertThat(block.build(BlockContext.of("/", "Home"), Map.of())).isEmpty();
    }

    @Test
    void aContextualFilterTakesItsValueFromThePath() throws Exception {
        ViewConfig byType = storyView().withDisplay(new ViewDisplay("by_type", ViewDisplay.PAGE, "By type",
                ViewOptions.INHERIT.withArguments(List.of(HandlerConfig.of("type", ValueArgument.ID,
                        NodeEntityType.BUNDLE_KEY, Map.of()))).withAccess(PluginConfig.of(NoneAccess.ID)),
                Map.of(ViewDisplay.PATH, "/stories/%/list")));
        views.save(byType);

        assertThat(rowTitles(page("/stories/" + STORY + "/list").selectFirst("#view-" + VIEW + "-by_type")))
                .containsExactly("Apples", "Bridges");
        assertThat(page("/stories/page/list").select("#view-" + VIEW + "-by_type tbody tr")).isEmpty();
        assertThat(mockMvc.perform(get("/stories/" + STORY + "/list/more").with(reader())).andReturn().getResponse()
                .getStatus()).isEqualTo(404);
    }

    @Test
    void adminViewPagesAreDrawnInTheAdminTheme() throws Exception {
        views.save(storyView().withDisplay(new ViewDisplay("admin", ViewDisplay.PAGE, "Story admin",
                ViewOptions.INHERIT, Map.of(ViewDisplay.PATH, "/admin/stories"))));

        MockHttpServletResponse response = mockMvc.perform(get("/admin/stories").with(reader())).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(Jsoup.parse(response.getContentAsString()).select("#view-" + VIEW + "-admin")).hasSize(1);
    }

    @Test
    void theCacheKeepsADrawingUntilTheListedContentOrTheViewChanges() throws Exception {
        page("/stories/table");
        int kept = cache.size();
        page("/stories/table");
        assertThat(cache.size()).isEqualTo(kept);

        story("Aardvarks", 20, true);
        assertThat(rowTitles(page("/stories/table").selectFirst("#view-" + VIEW + "-table")))
                .containsExactly("Aardvarks", "Apples");

        views.save(storyView().withDisplay(new ViewDisplay("table", ViewDisplay.PAGE, "Renamed table",
                ViewOptions.INHERIT, Map.of(ViewDisplay.PATH, "/stories/table"))));
        assertThat(page("/stories/table").title()).contains("Renamed table");
    }

    @Test
    void writesThatAreNotSavesLeaveTheCacheAlone() {
        cache.render(storyView(), "table", List.of(), Map.of(), "/stories/table", true, null);
        int kept = cache.size();

        nodes.find(((Number) queries.query(NodeEntityType.ID).ids().getFirst()).longValue());

        assertThat(cache.size()).isEqualTo(kept);
        cache.invalidate(ViewCache.listTag(NodeEntityType.ID));
        assertThat(cache.size()).isLessThan(kept);
    }

    @Test
    void aViewWithoutAStyleRowOrPagerFallsBackToPlainOnes() {
        ViewOptions bare = new ViewOptions(List.of(titleField(), HandlerConfig.of("x", "retired", "label",
                Map.of())), List.of(), List.of(), List.of(), List.of(), PluginConfig.of("retired"),
                PluginConfig.of("retired"), PluginConfig.of("retired"), null);
        ViewConfig plain = new ViewConfig("plain", "Plain", "", NodeEntityType.ID, List.of(new ViewDisplay(
                ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", bare, Map.of())));
        ViewConfig none = new ViewConfig("none", "None", "", NodeEntityType.ID, List.of(new ViewDisplay(
                ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", ViewOptions.INHERIT, Map.of())));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null,
                List.of(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT))));

        Document drawn = Jsoup.parseBodyFragment(renderer.render(plain, ViewDisplay.DEFAULT, List.of(),
                Map.of(ViewRenderer.PAGE, "two"), "/plain", true).html());
        Document nothing = Jsoup.parseBodyFragment(renderer.render(none, ViewDisplay.DEFAULT, List.of(), Map.of(),
                "/none", true).html());

        assertThat(drawn.select(".views-rows .views-row")).hasSize(5);
        assertThat(drawn.select("form, .pagination")).isEmpty();
        assertThat(nothing.select(".views-row")).hasSize(5);
        assertThat(nothing.select(".views-row").first().text()).isEmpty();
    }

    @Test
    void anExposedSortLetsTheReaderChooseTheOrder() {
        ViewOptions sortable = storyView().options(ViewDisplay.DEFAULT).withSorts(List.of(
                HandlerConfig.of("title", StandardSort.ID, "label", Map.of(ViewExecutor.EXPOSED, true, "label",
                        "Title")),
                HandlerConfig.of("created", StandardSort.ID, BaseFieldDefinition.CREATED, Map.of(ViewExecutor.EXPOSED,
                        true)),
                HandlerConfig.of("id", StandardSort.ID, "id", Map.of())))
                .withPager(PluginConfig.of("none")).withAccess(PluginConfig.of(NoneAccess.ID));
        ViewConfig view = new ViewConfig("sorted", "Sorted", "", NodeEntityType.ID, List.of(new ViewDisplay(
                ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", sortable, Map.of())));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null,
                List.of(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT))));

        Document byDefault = Jsoup.parseBodyFragment(renderer.render(view, ViewDisplay.DEFAULT, List.of(), Map.of(),
                "/sorted", true).html());
        Document newest = Jsoup.parseBodyFragment(renderer.render(view, ViewDisplay.DEFAULT, List.of(),
                Map.of(ViewExecutor.SORT_BY, "created", ViewExecutor.SORT_ORDER, "desc"), "/sorted", true).html());
        Document oldest = Jsoup.parseBodyFragment(renderer.render(view, ViewDisplay.DEFAULT, List.of(),
                Map.of(ViewExecutor.SORT_BY, "created", ViewExecutor.SORT_ORDER, "asc"), "/sorted", true).html());
        Document unknownOrder = Jsoup.parseBodyFragment(renderer.render(view, ViewDisplay.DEFAULT, List.of(),
                Map.of(ViewExecutor.SORT_BY, "created", ViewExecutor.SORT_ORDER, "sideways"), "/sorted", true).html());

        assertThat(byDefault.select("select[name=sort_by] option").eachText()).containsExactly("Title", "created");
        assertThat(byDefault.select("select[name=sort_order] option").eachAttr("value")).containsExactly("asc", "desc");
        assertThat(rowTitles(byDefault.selectFirst(".view"))).startsWith("Apples", "Bridges");
        assertThat(rowTitles(newest.selectFirst(".view"))).startsWith("Elms", "Docks");
        assertThat(rowTitles(oldest.selectFirst(".view"))).endsWith("Docks", "Elms");
        assertThat(rowTitles(unknownOrder.selectFirst(".view"))).startsWith("Bridges", "Apples");
        assertThat(Optional.ofNullable(newest.selectFirst("select[name=sort_by] option[selected]"))
                .map(Element::val)).contains("created");
    }

    private static ViewConfig untitledView(List<HandlerConfig> relationships) {
        ViewOptions defaults = new ViewOptions(List.of(titleField()), List.of(HandlerConfig.of("type",
                ValueFilter.ID, NodeEntityType.BUNDLE_KEY, Map.of(FilterHandler.VALUE, STORY)),
                HandlerConfig.of("gone", "retired", "label", Map.of(FilterHandler.EXPOSED, true))),
                List.of(HandlerConfig.of("title", StandardSort.ID, "label", Map.of())), List.of(), relationships,
                PluginConfig.of("none"), null, null, PluginConfig.of(NoneAccess.ID));
        return new ViewConfig("untitled", "Untitled", "", NodeEntityType.ID, List.of(
                new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", defaults, Map.of()),
                new ViewDisplay("bare", ViewDisplay.PAGE, "", ViewOptions.INHERIT, Map.of(ViewDisplay.PATH, "/bare",
                        ViewDisplay.MENU_TITLE, "Bare", ViewDisplay.MENU, "footer", ViewDisplay.EXPOSED_BLOCK, true)),
                new ViewDisplay("bare_again", ViewDisplay.PAGE, "Bare again", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/bare")),
                new ViewDisplay("side", ViewDisplay.BLOCK, "", ViewOptions.INHERIT, Map.of()),
                new ViewDisplay("bare_feed", ViewDisplay.FEED, "Bare feed", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/bare/feed"))));
    }

    @Test
    void displaysWithoutTitlesAreNamedByTheViewOrTheirIds() throws Exception {
        views.save(untitledView(null));

        assertThat(page("/bare").title()).contains("Untitled");
        assertThat(mockMvc.perform(head("/bare").with(reader())).andReturn().getResponse().getStatus())
                .isEqualTo(200);
        assertThat(registry.managerFor(BlockPlugin.class).get(ViewBlockDeriver.ID + ":untitled-side").label())
                .isEqualTo("Untitled: side");
        BlockPlugin exposed = registry.managerFor(BlockPlugin.class).get(ExposedFormBlockDeriver.ID
                + ":untitled-bare");
        assertThat(exposed.label()).isEqualTo("Exposed form: Untitled: bare");
        assertThat(exposed.build(BlockContext.of("/", "Home"), Map.of())).isEmpty();
        assertThat(viewMenuLinks.menuLinks()).extracting(MenuLink::menu, MenuLink::title)
                .contains(org.assertj.core.groups.Tuple.tuple("footer", "Bare"));
    }

    @Test
    void aFeedLeavesOutThePublicationDateOfContentWithoutOne() throws Exception {
        views.save(untitledView(List.of(HandlerConfig.of("gone", "retired", BaseFieldDefinition.OWNER, Map.of()))));
        entities.save(EntityData.of(NodeEntityType.ID, null, STORY, "Undated",
                Map.of(BaseFieldDefinition.STATUS, true)));

        Document rss = Jsoup.parse(mockMvc.perform(get("/bare/feed").with(reader())).andReturn().getResponse()
                .getContentAsString(), "", org.jsoup.parser.Parser.xmlParser());

        assertThat(rss.select("item:has(title:containsOwn(Undated)) > pubDate")).isEmpty();
        assertThat(rss.select("item > pubDate")).hasSize(5);
    }

    @Test
    void aViewOfRenderedEntitiesDrawsOtherEntitiesThroughTheirViewDisplay() {
        long edith = accounts.create("edith_rows", "edith_rows@example.com", "").id();
        ViewConfig people = new ViewConfig("people_rows", "People rows", "", UserEntityType.ID, List.of(
                new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "", new ViewOptions(List.of(), List.of(),
                        List.of(), List.of(), List.of(), null, null, PluginConfig.of(EntityRow.ID),
                        PluginConfig.of(NoneAccess.ID)), Map.of())));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AccountPrincipal(1L, "admin", "", true, List.of()), null, List.of()));
        try {
            Document drawn = Jsoup.parseBodyFragment(renderer.render(people, ViewDisplay.DEFAULT, List.of(), Map.of(),
                    "/people-rows", false).html());

            assertThat(drawn.select(".views-row")).isNotEmpty();
        } finally {
            entities.delete(UserEntityType.ID, edith);
        }
    }
}
