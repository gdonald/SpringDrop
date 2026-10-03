package dev.springdrop.kernel.search;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.TextWithSummaryFieldType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.FilterHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;

@SpringBootTest
class SearchIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "search_page";

    private static final String BODY = "field_search_body";

    private static final String SUBTITLE = "field_search_subtitle";

    private static final String BUILT = "field_search_built";

    @Autowired
    private SearchService search;

    @Autowired
    private SearchIndexer indexer;

    @Autowired
    private CronRunner cron;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private PluginRegistry registry;

    @BeforeEach
    void aPageTypeWithTextFields() {
        accounts.install();
        dsl.deleteFrom(DSL.table("search_pending")).execute();
        nodeTypes.save(NodeType.of(PAGE, "Page"));
        fields.createStorage(FieldStorageConfig.single(BODY, NodeEntityType.ID, TextWithSummaryFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(BODY, NodeEntityType.ID, PAGE, "Body"));
        fields.createStorage(new FieldStorageConfig(SUBTITLE, NodeEntityType.ID, StringFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, PAGE, "Subtitles"));
        fields.createStorage(FieldStorageConfig.single(BUILT, NodeEntityType.ID, IntegerFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(BUILT, NodeEntityType.ID, PAGE, "Built"));
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.deleteStorage(NodeEntityType.ID, BODY);
        fields.deleteStorage(NodeEntityType.ID, SUBTITLE);
        fields.deleteStorage(NodeEntityType.ID, BUILT);
        nodeTypes.delete(PAGE);
        search.backend().clear(NodeEntityType.ID);
        dsl.deleteFrom(DSL.table("search_pending")).execute();
    }

    private EntityData page(String title, String body, List<String> subtitles) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BODY, Map.of(FormattedText.VALUE, body, FormattedText.SUMMARY, "", FormattedText.FORMAT,
                "basic_html"));
        values.put(SUBTITLE, subtitles);
        values.put(BUILT, 1850);
        return nodes.save(EntityData.of(NodeEntityType.ID, null, PAGE, title, values), 1L);
    }

    private List<String> titles(String keywords) {
        return search.search(new SearchQuery(NodeEntityType.ID, keywords, 0, 10)).hits().stream()
                .map(SearchHit::title).toList();
    }

    private static long id(EntityData node) {
        return ((Number) node.id()).longValue();
    }

    @Test
    void indexedContentIsFoundByKeywordWithItsTitleRankedFirst() {
        page("Canal walks", "<p>The towpath runs beside the old <b>bridges</b>.</p>", List.of());
        page("Bridges of the city", "<p>Stone and iron crossings.</p>", List.of("A guide", "Maps"));
        page("Opening hours", "<p>Open daily.</p>", List.of());

        assertThat(indexer.pending()).isEqualTo(3);
        cron.run();

        assertThat(indexer.pending()).isZero();
        assertThat(titles("bridge")).containsExactly("Bridges of the city", "Canal walks");
        assertThat(titles("guide")).containsExactly("Bridges of the city");
        assertThat(titles("maps")).containsExactly("Bridges of the city");
        assertThat(titles("1850")).isEmpty();
        assertThat(titles("bridges towpath")).containsExactly("Canal walks");
        assertThat(titles("museum")).isEmpty();
    }

    @Test
    void anEditedNodeIsIndexedAgainAndADeletedOneLeavesTheIndex() {
        EntityData hours = page("Summer hours", "<p>Open daily.</p>", List.of());
        EntityData closed = page("Closed days", "<p>Closed on holidays.</p>", List.of());
        indexer.indexPending(SearchIndexer.BATCH);

        nodes.save(new EntityData(hours.entityType(), hours.id(), hours.uuid(), hours.bundle(), "Winter hours",
                hours.langcode(), hours.revisionId(), hours.fields()), 1L);
        assertThat(titles("winter")).isEmpty();
        indexer.indexPending(SearchIndexer.BATCH);
        nodes.delete(id(closed));

        assertThat(titles("winter")).containsExactly("Winter hours");
        assertThat(titles("summer")).isEmpty();
        assertThat(titles("holidays")).isEmpty();
    }

    @Test
    void anExcerptMarksTheMatchedWordsAndEscapesTheRest() {
        page("Tides", "<p>Low tide &amp; high tide: 5 &lt; 6, see \u0002the\u0003 harbor.</p>", List.of());
        indexer.indexPending(SearchIndexer.BATCH);

        SearchHit hit = search.search(new SearchQuery(NodeEntityType.ID, "harbor", 0, 10)).hits().getFirst();

        assertThat(hit.excerpt()).contains("<mark>harbor</mark>").contains("&amp; high").contains("5 &lt; 6")
                .doesNotContain("<mark>the</mark>");
        assertThat(hit.rank()).isPositive();
        assertThat(hit.entityType()).isEqualTo(NodeEntityType.ID);
        assertThat(hit.langcode()).isEqualTo(EntityData.DEFAULT_LANGCODE);
    }

    @Test
    void resultsArePagedAndCounted() {
        for (String title : List.of("Ferry one", "Ferry two", "Ferry three")) {
            page(title, "", List.of());
        }
        indexer.indexPending(SearchIndexer.BATCH);

        SearchResults second = search.search(new SearchQuery(NodeEntityType.ID, "ferry", 2, 2));

        assertThat(second.total()).isEqualTo(3);
        assertThat(second.hits()).hasSize(1);
        assertThat(search.search(new SearchQuery(NodeEntityType.ID, "?!", 0, 10))).isEqualTo(SearchResults.NONE);
    }

    @Test
    void everySearchableEntityCanBeMarkedForIndexingAgain() {
        page("Ferry one", "", List.of());
        page("Ferry two", "", List.of());
        indexer.indexPending(SearchIndexer.BATCH);
        search.backend().clear(NodeEntityType.ID);
        assertThat(titles("ferry")).isEmpty();

        indexer.markAllPending();
        assertThat(indexer.indexPending(1)).isOne();
        indexer.indexPending(SearchIndexer.BATCH);

        assertThat(titles("ferry")).hasSize(2);
        assertThat(indexer.entityTypes()).containsExactly(NodeEntityType.ID, UserEntityType.ID);
    }

    @Test
    void pendingEntitiesThatAreGoneOrNoLongerSearchableAreDropped() {
        EntityData ferry = page("Ferry", "", List.of());
        indexer.indexPending(SearchIndexer.BATCH);
        dsl.insertInto(DSL.table("search_pending")).columns(DSL.field("entity_type"), DSL.field("entity_id"))
                .values(NodeEntityType.ID, id(ferry) + 1_000_000).values("retired_type", 7L).execute();

        indexer.indexPending(SearchIndexer.BATCH);

        assertThat(indexer.pending()).isZero();
        assertThat(titles("ferry")).containsExactly("Ferry");
    }

    @Test
    void entitiesOfTypesWithoutADocumentBuilderAndOtherEventsAreLeftAlone() {
        events.publishEvent(new EntityEvent(EntityData.of("vocabulary_like", 7L, null, "Tags", Map.of()),
                "vocabulary_like", EntityEvent.Phase.INSERT));
        events.publishEvent(new EntityEvent("not an entity", NodeEntityType.ID, EntityEvent.Phase.INSERT));
        events.publishEvent(new EntityEvent(EntityData.of(NodeEntityType.ID, 7L, PAGE, "Loaded", Map.of()),
                NodeEntityType.ID, EntityEvent.Phase.LOAD));

        assertThat(indexer.pending()).isZero();
        assertThat(indexer.id()).isEqualTo("search.index");
        assertThat(search.backend().id()).isEqualTo(PostgresSearchBackend.ID);
    }

    @Test
    void theKeywordsFilterAndTheExcerptFieldTakeTheirSettings() {
        SearchKeywordsFilter filter = (SearchKeywordsFilter) registry.managerFor(FilterHandler.class)
                .get(SearchKeywordsFilter.ID);
        SearchExcerptField excerpt = (SearchExcerptField) registry.managerFor(FieldHandler.class)
                .get(SearchExcerptField.ID);

        assertThat(filter.settingsValues("s_", Map.of("s_" + SearchKeywordsFilter.ENTITY_TYPE, "user",
                "s_" + FilterHandler.EXPOSED, "true", "s_" + FilterHandler.IDENTIFIER, "q")))
                .containsEntry(SearchKeywordsFilter.ENTITY_TYPE, "user").containsEntry(FilterHandler.EXPOSED, true)
                .containsEntry(FilterHandler.IDENTIFIER, "q");
        assertThat(filter.settingsForm("s_", Map.of())).hasSize(5);
        assertThat(filter.label()).isEqualTo("Search keywords");
        assertThat(filter.exposedElement(HandlerConfig.of("keys", SearchKeywordsFilter.ID, "id", Map.of()), "x")
                .label()).isEqualTo("Keywords");
        assertThat(excerpt.settingsValues("s_", Map.of("s_" + SearchExcerptField.IDENTIFIER, "q")))
                .containsEntry(SearchExcerptField.IDENTIFIER, "q");
        assertThat(excerpt.settingsForm("s_", Map.of())).hasSize(2);
        assertThat(List.of(excerpt.label(), excerpt.sortable())).containsExactly("Search excerpt", false);
    }

    @Test
    void anExcerptReadsTheKeywordsUnderItsIdentifierAndIsEmptyForAResultNotFound() {
        EntityData tides = page("Tides", "<p>Harbor tides.</p>", List.of());
        EntityData hours = page("Hours", "<p>Open daily.</p>", List.of());
        indexer.indexPending(SearchIndexer.BATCH);
        SearchExcerptField excerpt = (SearchExcerptField) registry.managerFor(FieldHandler.class)
                .get(SearchExcerptField.ID);
        HandlerConfig underQ = HandlerConfig.of("excerpt", SearchExcerptField.ID, "id",
                Map.of(SearchExcerptField.IDENTIFIER, "q"));

        assertThat(excerpt.render(new ResultRow(tides, Map.of(), Map.of("q", "harbor")), underQ))
                .contains("<mark>Harbor</mark>");
        assertThat(excerpt.render(new ResultRow(hours, Map.of(), Map.of("q", "harbor")), underQ)).isEmpty();
        assertThat(search.ranked(NodeEntityType.ID, "  ")).isEmpty();
    }

    @Test
    void outsideARequestEachRankedSearchAsksTheBackend() {
        page("Tides", "<p>Harbor tides.</p>", List.of());
        indexer.indexPending(SearchIndexer.BATCH);
        RequestContextHolder.resetRequestAttributes();

        assertThat(search.ranked(NodeEntityType.ID, "harbor")).extracting(SearchHit::title).containsExactly("Tides");
        assertThat(RequestContextHolder.getRequestAttributes()).isNull();
    }
}
