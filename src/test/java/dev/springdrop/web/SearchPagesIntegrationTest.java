package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.search.SearchIndexer;
import dev.springdrop.kernel.search.SearchPage;
import dev.springdrop.kernel.search.SearchPageManager;
import dev.springdrop.kernel.search.SearchService;
import dev.springdrop.kernel.search.SearchViews;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
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
class SearchPagesIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "search_web_page";

    private static final String BODY = "field_search_web_body";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchIndexer indexer;

    @Autowired
    private SearchService search;

    @Autowired
    private SearchPageManager searchPages;

    @Autowired
    private ViewManager views;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private DSLContext dsl;

    private final List<Long> people = new ArrayList<>();

    @BeforeEach
    void pagesAndPeople() {
        accounts.install();
        searchPages.install();
        nodeTypes.save(NodeType.of(PAGE, "Page"));
        fields.createStorage(FieldStorageConfig.single(BODY, NodeEntityType.ID, TextLongFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(BODY, NodeEntityType.ID, PAGE, "Body"));
        page("Bridges of the city", "<p>Stone and iron crossings over the river.</p>", true, 1L);
        page("Canal walks", "<p>The towpath passes under twelve bridges.</p>", true, 1L);
        page("Bridge repairs", "<p>A draft about bridge repairs.</p>", false, 1L);
        page("Opening hours", "<p>Open daily.</p>", true, 1L);
        people.add(accounts.create("searcher_edith", "searcher_edith@example.com", "").id());
        long blocked = accounts.create("searcher_edith_blocked", "searcher_edith_blocked@example.com", "").id();
        accounts.block(blocked);
        people.add(blocked);
        indexer.indexPending(1_000);
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.deleteStorage(NodeEntityType.ID, BODY);
        nodeTypes.delete(PAGE);
        people.forEach(id -> entities.delete(UserEntityType.ID, id));
        search.backend().clear(NodeEntityType.ID);
        search.backend().clear(UserEntityType.ID);
        dsl.deleteFrom(DSL.table("search_pending")).execute();
        searchPages.all().forEach(page -> searchPages.delete(page.id()));
        views.delete(SearchViews.CONTENT);
        views.delete(SearchViews.USERS);
    }

    private EntityData page(String title, String body, boolean published, long owner) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BaseFieldDefinition.STATUS, published);
        values.put(BODY, Map.of(FormattedText.VALUE, body, FormattedText.FORMAT, "basic_html"));
        return nodes.save(EntityData.of(NodeEntityType.ID, null, PAGE, title, values), owner);
    }

    private static RequestPostProcessor reader(String... permissions) {
        return user("reader").authorities(java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new)
                .toList());
    }

    private static RequestPostProcessor contentReader() {
        return reader(SearchViews.SEARCH_CONTENT, NodePermissions.ACCESS_CONTENT);
    }

    private MockHttpServletResponse response(String path, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(get(path).with(who)).andReturn().getResponse();
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(response(path, who).getContentAsString());
    }

    private static List<String> titles(Document page) {
        return page.select(".view-" + SearchViews.CONTENT + " .views-field-title a").eachText();
    }

    @Test
    void aSearchReturnsOnlyViewableResultsRankedWithHighlightedExcerpts() throws Exception {
        Document results = page("/search/node?keys=bridge", contentReader());

        assertThat(titles(results)).containsExactly("Bridges of the city", "Canal walks");
        assertThat(results.select(".views-field-excerpt mark").eachText()).contains("bridges");
        assertThat(results.selectFirst("form.views-exposed-form input[name=keys]").val()).isEqualTo("bridge");
        assertThat(results.selectFirst("form.views-exposed-form").attr("hx-get")).isEqualTo("/search/node");
        assertThat(titles(page("/search/node?keys=bridge", reader(SearchViews.SEARCH_CONTENT,
                NodePermissions.ACCESS_CONTENT, "bypass node access")))).hasSize(3);
    }

    @Test
    void withoutKeywordsOnlyTheFormIsShownAndNothingFoundSaysSo() throws Exception {
        Document blank = page("/search/node", contentReader());
        Document nothing = page("/search/node?keys=museum", contentReader());

        assertThat(blank.select("form.views-exposed-form input[name=keys]")).hasSize(1);
        assertThat(blank.select(".views-row, .views-empty")).isEmpty();
        assertThat(blank.selectFirst("#view-" + SearchViews.CONTENT + "-default")).isNotNull();
        assertThat(nothing.selectFirst(".views-empty").text()).isEqualTo("Your search yielded no results.");
    }

    @Test
    void newlyIndexedContentShowsInASearchShownBefore() throws Exception {
        assertThat(titles(page("/search/node?keys=ferry", contentReader()))).isEmpty();

        page("Ferry times", "<p>Boats leave hourly.</p>", true, 1L);
        indexer.indexPending(1_000);

        assertThat(titles(page("/search/node?keys=ferry", contentReader()))).containsExactly("Ferry times");
    }

    @Test
    void theUserSearchFindsActiveAccountsByName() throws Exception {
        Document found = page("/search/user?keys=searcher+edith", reader(SearchViews.SEARCH_USERS));

        assertThat(found.select(".view-" + SearchViews.USERS + " .views-field-name").eachText())
                .containsExactly("searcher_edith");
        assertThat(found.select(".view-" + SearchViews.USERS + " .views-field-name a")).isEmpty();
    }

    @Test
    void eachSearchPageTheReaderMayUseHasATab() throws Exception {
        Document both = page("/search/node", reader(SearchViews.SEARCH_CONTENT, SearchViews.SEARCH_USERS));
        Document one = page("/search/node", contentReader());

        assertThat(both.select("a[href=/search/node], a[href=/search/user]").eachText()).contains("Content",
                "Users");
        assertThat(one.select("a[href=/search/user]")).isEmpty();
        assertThat(both.title()).contains("Search");
    }

    @Test
    void theSearchAddressLeadsToTheFirstSearchTheReaderMayUse() throws Exception {
        assertThat(response("/search", contentReader()).getRedirectedUrl()).isEqualTo("/search/node");
        assertThat(response("/search", reader(SearchViews.SEARCH_USERS)).getRedirectedUrl())
                .isEqualTo("/search/user");
        assertThat(response("/search", reader()).getStatus()).isEqualTo(403);
    }

    @Test
    void aSearchPageTheReaderMayNotUseOrTheSiteLacksIsRefused() throws Exception {
        assertThat(response("/search/user", contentReader()).getStatus()).isEqualTo(403);
        assertThat(response("/search/nothing", contentReader()).getStatus()).isEqualTo(404);

        views.delete(SearchViews.CONTENT);

        assertThat(response("/search/node", contentReader()).getStatus()).isEqualTo(403);
    }

    @Test
    void searchPagesAreKeptByWeightAndASavedOneIsLeftAsItIs() {
        searchPages.save(new SearchPage(SearchPageManager.CONTENT, "Articles", "node", SearchViews.CONTENT, 5));

        searchPages.install();

        assertThat(searchPages.all()).extracting(SearchPage::label).containsExactly("Users", "Articles");
        assertThat(searchPages.atPath("user")).map(SearchPage::address).contains("/search/user");
    }
}
