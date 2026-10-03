package dev.springdrop.kernel.search;

import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Keeps documents in the {@code search_index} table, whose {@code tsvector}
 * column Postgres maintains with the {@code english} configuration. A search
 * matches every keyword through {@code to_tsquery}, ranks with {@code ts_rank},
 * and excerpts the body with {@code ts_headline}.
 */
@Component
public class PostgresSearchBackend implements SearchBackend {

    public static final String ID = "postgres";

    static final String TEXT_SEARCH_CONFIG = "english";

    /** Marks ts_headline puts around matched words, characters no indexed text holds once stripped of tags. */
    private static final String START = "\u0002";

    private static final String STOP = "\u0003";

    private static final Table<?> INDEX = DSL.table(DSL.name("search_index"));
    private static final Field<String> ENTITY_TYPE = DSL.field(DSL.name("entity_type"), SQLDataType.VARCHAR);
    private static final Field<Long> ENTITY_ID = DSL.field(DSL.name("entity_id"), SQLDataType.BIGINT);
    private static final Field<String> LANGCODE = DSL.field(DSL.name("langcode"), SQLDataType.VARCHAR);
    private static final Field<String> TITLE = DSL.field(DSL.name("title"), SQLDataType.CLOB);
    private static final Field<String> BODY = DSL.field(DSL.name("body"), SQLDataType.CLOB);

    private final DSLContext dsl;

    public PostgresSearchBackend(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void index(SearchDocument document) {
        dsl.insertInto(INDEX).columns(ENTITY_TYPE, ENTITY_ID, LANGCODE, TITLE, BODY)
                .values(document.entityType(), document.entityId(), document.langcode(), clean(document.title()),
                        clean(document.body()))
                .onConflict(ENTITY_TYPE, ENTITY_ID, LANGCODE)
                .doUpdate().set(TITLE, clean(document.title())).set(BODY, clean(document.body()))
                .execute();
    }

    @Override
    public void remove(String entityType, long entityId) {
        dsl.deleteFrom(INDEX).where(ENTITY_TYPE.eq(entityType)).and(ENTITY_ID.eq(entityId)).execute();
    }

    @Override
    public void clear(String entityType) {
        dsl.deleteFrom(INDEX).where(ENTITY_TYPE.eq(entityType)).execute();
    }

    @Override
    public SearchResults search(SearchQuery query) {
        List<String> words = SearchKeywords.words(query.keywords());
        if (words.isEmpty()) {
            return SearchResults.NONE;
        }
        Field<Object> tsquery = DSL.field("to_tsquery({0}::regconfig, {1})", Object.class,
                DSL.inline(TEXT_SEARCH_CONFIG), DSL.val(String.join(" & ", words)));
        Field<Object> document = DSL.field(DSL.name("document"), Object.class);
        var matches = DSL.condition("{0} @@ {1}", document, tsquery);
        var where = ENTITY_TYPE.eq(query.entityType()).and(matches);
        long total = dsl.fetchCount(INDEX, where);
        Field<Double> rank = DSL.field("ts_rank({0}, {1})", SQLDataType.DOUBLE, document, tsquery);
        Field<String> headline = DSL.field("ts_headline({0}::regconfig, {1}, {2}, {3})", SQLDataType.CLOB,
                DSL.inline(TEXT_SEARCH_CONFIG), BODY, tsquery,
                DSL.val("StartSel=" + START + ", StopSel=" + STOP + ", MaxWords=35, MinWords=15"));
        List<SearchHit> hits = dsl.select(ENTITY_TYPE, ENTITY_ID, LANGCODE, TITLE, rank, headline)
                .from(INDEX).where(where)
                .orderBy(rank.desc(), ENTITY_ID.desc())
                .offset(query.offset()).limit(query.limit())
                .fetch(row -> new SearchHit(row.get(ENTITY_TYPE), row.get(ENTITY_ID), row.get(LANGCODE),
                        row.get(TITLE), row.get(rank), marked(row.get(headline))));
        return new SearchResults(hits, total);
    }

    /** The excerpt as HTML: its text escaped and the matched words in {@code mark}. */
    private static String marked(String headline) {
        return HtmlUtils.htmlEscape(headline).replace(START, "<mark>").replace(STOP, "</mark>");
    }

    /** Text without the marks the excerpt uses, so text indexed cannot pose as a match. */
    private static String clean(String text) {
        return text.replace(START, "").replace(STOP, "");
    }
}
