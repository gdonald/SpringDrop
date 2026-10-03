package dev.springdrop.kernel.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SearchBackendsIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "backend_page";

    private static final String BODY = "field_backend_body";

    private static final List<String> KEYWORDS = List.of("bridge", "towpath", "river crossings", "museum",
            "the", "hours");

    @Autowired
    private SearchService search;

    @Autowired
    private SearchIndexer indexer;

    @Autowired
    private LuceneSearchBackend lucene;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void pagesIndexedInPostgres() {
        accounts.install();
        dsl.deleteFrom(DSL.table("search_pending")).execute();
        nodeTypes.save(NodeType.of(PAGE, "Page"));
        fields.createStorage(FieldStorageConfig.single(BODY, NodeEntityType.ID, TextLongFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(BODY, NodeEntityType.ID, PAGE, "Body"));
        page("Bridges of the city", "Stone and iron crossings over the river.");
        page("Canal walks", "The towpath passes under twelve bridges.");
        page("Opening hours", "Open daily from nine.");
        indexer.indexPending(SearchIndexer.BATCH);
    }

    @AfterEach
    void backToPostgres() {
        configStore.delete(SearchSettings.CONFIG_NAME);
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.deleteStorage(NodeEntityType.ID, BODY);
        nodeTypes.delete(PAGE);
        for (SearchBackend backend : List.of(lucene, search.backend())) {
            backend.clear(NodeEntityType.ID);
            backend.clear(UserEntityType.ID);
        }
        dsl.deleteFrom(DSL.table("search_pending")).execute();
    }

    private EntityData page(String title, String body) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BODY, Map.of(FormattedText.VALUE, "<p>" + body + "</p>", FormattedText.FORMAT, "basic_html"));
        return nodes.save(EntityData.of(NodeEntityType.ID, null, PAGE, title, values), 1L);
    }

    private Map<String, List<String>> results() {
        Map<String, List<String>> found = new LinkedHashMap<>();
        for (String keywords : KEYWORDS) {
            found.put(keywords, search.search(new SearchQuery(NodeEntityType.ID, keywords, 0, 10)).hits().stream()
                    .map(SearchHit::title).toList());
        }
        return found;
    }

    @Test
    void switchingTheBackendReindexesAndServesTheSameResults() {
        Map<String, List<String>> fromPostgres = results();

        int indexed = indexer.switchBackend(LuceneSearchBackend.ID);

        assertThat(search.backend().id()).isEqualTo(LuceneSearchBackend.ID);
        assertThat(indexed).isGreaterThanOrEqualTo(3);
        assertThat(results()).isEqualTo(fromPostgres);
        assertThat(fromPostgres.get("bridge")).containsExactly("Bridges of the city", "Canal walks");
        assertThat(search.search(new SearchQuery(NodeEntityType.ID, "towpath", 0, 10)).hits().getFirst().excerpt())
                .contains("<mark>towpath</mark>");
    }

    @Test
    void theLuceneBackendPagesCountsReplacesAndRemovesDocuments() {
        indexer.switchBackend(LuceneSearchBackend.ID);
        EntityData canal = page("Canal walks", "Locks & weirs: 5 < 6.");
        indexer.indexPending(SearchIndexer.BATCH);

        SearchResults second = search.search(new SearchQuery(NodeEntityType.ID, "canal", 1, 1));
        SearchResults opening = search.search(new SearchQuery(NodeEntityType.ID, "weirs", 0, 10));

        assertThat(second.total()).isEqualTo(2);
        assertThat(second.hits()).hasSize(1);
        assertThat(opening.hits().getFirst().excerpt()).isEqualTo("Locks &amp; <mark>weirs</mark>: 5 &lt; 6.");
        assertThat(search.search(new SearchQuery(NodeEntityType.ID, "canal walks", 0, 10)).hits().getFirst()
                .excerpt()).isEqualTo("Locks &amp; weirs: 5 &lt; 6.");

        nodes.delete(((Number) canal.id()).longValue());
        assertThat(search.search(new SearchQuery(NodeEntityType.ID, "weirs", 0, 10)).hits()).isEmpty();
        lucene.clear(NodeEntityType.ID);
        assertThat(search.search(new SearchQuery(NodeEntityType.ID, "bridge", 0, 10)).total()).isZero();
        assertThat(search.search(new SearchQuery(NodeEntityType.ID, "the of", 0, 10))).isEqualTo(SearchResults.NONE);
    }

    @Test
    void aLuceneIndexNotYetWrittenFindsNothing() throws Exception {
        LuceneSearchBackend fresh = new LuceneSearchBackend(new SearchProperties(Files.createTempDirectory(
                FILES, "unwritten").resolve("index").toString()));

        assertThat(fresh.search(new SearchQuery(NodeEntityType.ID, "bridge", 0, 10))).isEqualTo(SearchResults.NONE);
        assertThat(fresh.id()).isEqualTo(LuceneSearchBackend.ID);
    }

    @Test
    void aLuceneIndexThatCannotBeOpenedIsReported() throws Exception {
        LuceneSearchBackend broken = new LuceneSearchBackend(new SearchProperties(Files.createTempFile(FILES,
                "not-a-directory", ".txt").toString()));

        assertThatThrownBy(() -> broken.remove(NodeEntityType.ID, 1L)).isInstanceOf(UncheckedIOException.class);
        assertThatThrownBy(() -> broken.search(new SearchQuery(NodeEntityType.ID, "bridge", 0, 10)))
                .isInstanceOf(IllegalStateException.class).hasMessage("The search index could not be read.");
    }

    @Test
    void aBackendTheSiteLacksIsRefusedAndAStoredOneItLacksFallsBackToPostgres() {
        assertThatThrownBy(() -> search.choose("solr")).isInstanceOf(IllegalArgumentException.class);

        configStore.save(SearchSettings.CONFIG_NAME, new SearchSettings("solr"));

        assertThat(search.backend().id()).isEqualTo(PostgresSearchBackend.ID);
        assertThat(search.backendIds()).containsExactly(LuceneSearchBackend.ID, PostgresSearchBackend.ID);
    }

    @Test
    void theReindexCommandBuildsTheIndexAgainOnlyWhenAsked() {
        search.backend().clear(NodeEntityType.ID);

        new SearchReindexCommand(new DefaultApplicationArguments(), indexer).run();
        assertThat(results().get("bridge")).isEmpty();
        new SearchReindexCommand(new DefaultApplicationArguments("--" + SearchReindexCommand.OPTION), indexer).run();

        assertThat(results().get("bridge")).containsExactly("Bridges of the city", "Canal walks");
    }
}
