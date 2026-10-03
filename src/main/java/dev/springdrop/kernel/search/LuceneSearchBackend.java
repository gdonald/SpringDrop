package dev.springdrop.kernel.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Keeps documents in a Lucene index on disk, analyzed with Lucene's English
 * analyzer. A search matches every keyword in the title or the body, a title
 * match counting twice, ranks by score and then the newest id, and excerpts
 * the body with Lucene's highlighter. The index is opened for each operation,
 * so more than one instance of the site in one process can share it.
 */
@Component
public class LuceneSearchBackend implements SearchBackend {

    public static final String ID = "lucene";

    private static final String START = "\u0002";

    private static final String STOP = "\u0003";

    private static final int EXCERPT_WORDS = 35;

    private static final float TITLE_BOOST = 2f;

    private static final String ENTITY = "entity";
    private static final String ENTITY_TYPE = "entity_type";
    private static final String ENTITY_ID = "entity_id";
    private static final String LANGCODE = "langcode";
    private static final String TITLE = "title";
    private static final String BODY = "body";

    /** One writer at a time in this process, whichever instance of the site writes. */
    private static final Object WRITING = new Object();

    private final Path location;
    private final Analyzer analyzer = new EnglishAnalyzer();

    public LuceneSearchBackend(SearchProperties properties) {
        this.location = Path.of(properties.lucenePath());
    }

    @Override
    public String id() {
        return ID;
    }

    private interface Write {
        void apply(IndexWriter writer) throws IOException;
    }

    private void write(Write change) {
        synchronized (WRITING) {
            try (Directory directory = FSDirectory.open(location);
                    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                change.apply(writer);
                writer.commit();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    private static String entityKey(String entityType, long entityId) {
        return entityType + "|" + entityId;
    }

    @Override
    public void index(SearchDocument document) {
        Document stored = new Document();
        stored.add(new StringField(ENTITY, entityKey(document.entityType(), document.entityId()), Field.Store.NO));
        stored.add(new StringField(ENTITY_TYPE, document.entityType(), Field.Store.YES));
        stored.add(new StoredField(ENTITY_ID, document.entityId()));
        stored.add(new NumericDocValuesField(ENTITY_ID, document.entityId()));
        stored.add(new StringField(LANGCODE, document.langcode(), Field.Store.YES));
        stored.add(new TextField(TITLE, clean(document.title()), Field.Store.YES));
        stored.add(new TextField(BODY, clean(document.body()), Field.Store.YES));
        Term key = new Term(ENTITY, entityKey(document.entityType(), document.entityId()));
        write(writer -> {
            writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new TermQuery(key), BooleanClause.Occur.FILTER)
                    .add(new TermQuery(new Term(LANGCODE, document.langcode())), BooleanClause.Occur.FILTER)
                    .build());
            writer.addDocument(stored);
        });
    }

    @Override
    public void remove(String entityType, long entityId) {
        write(writer -> writer.deleteDocuments(new Term(ENTITY, entityKey(entityType, entityId))));
    }

    @Override
    public void clear(String entityType) {
        write(writer -> writer.deleteDocuments(new Term(ENTITY_TYPE, entityType)));
    }

    /** The terms the analyzer makes of each keyword, the words it drops left out. */
    private List<String> terms(String keywords) throws IOException {
        List<String> terms = new ArrayList<>();
        for (String word : SearchKeywords.words(keywords)) {
            try (TokenStream tokens = analyzer.tokenStream(BODY, word)) {
                CharTermAttribute term = tokens.addAttribute(CharTermAttribute.class);
                tokens.reset();
                while (tokens.incrementToken()) {
                    terms.add(term.toString());
                }
                tokens.end();
            }
        }
        return terms.stream().distinct().toList();
    }

    @Override
    public SearchResults search(SearchQuery query) {
        try {
            List<String> terms = terms(query.keywords());
            if (terms.isEmpty()) {
                return SearchResults.NONE;
            }
            BooleanQuery.Builder matching = new BooleanQuery.Builder()
                    .add(new TermQuery(new Term(ENTITY_TYPE, query.entityType())), BooleanClause.Occur.FILTER);
            BooleanQuery.Builder inBody = new BooleanQuery.Builder();
            for (String term : terms) {
                matching.add(new BooleanQuery.Builder()
                        .add(new BoostQuery(new TermQuery(new Term(TITLE, term)), TITLE_BOOST),
                                BooleanClause.Occur.SHOULD)
                        .add(new TermQuery(new Term(BODY, term)), BooleanClause.Occur.SHOULD)
                        .build(), BooleanClause.Occur.MUST);
                inBody.add(new TermQuery(new Term(BODY, term)), BooleanClause.Occur.SHOULD);
            }
            return search(matching.build(), inBody.build(), query);
        } catch (IOException | InvalidTokenOffsetsException failure) {
            throw new IllegalStateException("The search index could not be read.", failure);
        }
    }

    private SearchResults search(Query matching, Query inBody, SearchQuery query)
            throws IOException, InvalidTokenOffsetsException {
        try (Directory directory = FSDirectory.open(location)) {
            return DirectoryReader.indexExists(directory) ? read(directory, matching, inBody, query)
                    : SearchResults.NONE;
        }
    }

    private SearchResults read(Directory directory, Query matching, Query inBody, SearchQuery query)
            throws IOException, InvalidTokenOffsetsException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            int total = searcher.count(matching);
            TopFieldDocs top = searcher.search(matching, Math.max(1, query.offset() + query.limit()),
                    new Sort(SortField.FIELD_SCORE, new SortField(ENTITY_ID, SortField.Type.LONG, true)), true);
            StoredFields stored = searcher.storedFields();
            Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter(START, STOP),
                    new QueryScorer(inBody, BODY));
            List<SearchHit> hits = new ArrayList<>();
            ScoreDoc[] found = top.scoreDocs;
            for (int index = query.offset(); index < Math.min(found.length, query.offset() + query.limit());
                    index++) {
                FieldDoc hit = (FieldDoc) found[index];
                Document document = stored.document(hit.doc);
                String body = document.get(BODY);
                String fragment = highlighter.getBestFragment(analyzer, BODY, body);
                hits.add(new SearchHit(document.get(ENTITY_TYPE),
                        document.getField(ENTITY_ID).numericValue().longValue(), document.get(LANGCODE),
                        document.get(TITLE), hit.score, marked(fragment == null ? opening(body) : fragment)));
            }
            return new SearchResults(hits, total);
        }
    }

    /** The first words of a body no keyword was found in. */
    private static String opening(String body) {
        return Arrays.stream(body.strip().split("\\s+")).limit(EXCERPT_WORDS).collect(Collectors.joining(" "));
    }

    private static String marked(String excerpt) {
        return HtmlUtils.htmlEscape(excerpt).replace(START, "<mark>").replace(STOP, "</mark>");
    }

    private static String clean(String text) {
        return text.replace(START, "").replace(STOP, "");
    }
}
