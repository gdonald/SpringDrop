package dev.springdrop.kernel.search;

import dev.springdrop.kernel.cron.CronJob;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.cache.CacheTagInvalidator;
import dev.springdrop.kernel.cache.CacheTags;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the index up to date with the searchable entity types. Saving an
 * entity of one marks it pending, and each cron run indexes up to
 * {@link #BATCH} pending entities. Deleting an entity removes it from the
 * index at once. Indexing an entity invalidates its type's list tag, so a
 * search kept in a cache shows the index as it is.
 */
@Component
public class SearchIndexer implements CronJob {

    public static final int BATCH = 100;

    private static final Table<?> PENDING = DSL.table(DSL.name("search_pending"));
    private static final Field<String> ENTITY_TYPE = DSL.field(DSL.name("entity_type"), SQLDataType.VARCHAR);
    private static final Field<Long> ENTITY_ID = DSL.field(DSL.name("entity_id"), SQLDataType.BIGINT);

    private final DSLContext dsl;
    private final SearchService search;
    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final Map<String, SearchDocumentBuilder> builders;
    private final CacheTagInvalidator invalidator;

    public SearchIndexer(DSLContext dsl, SearchService search, EntityCrudService entities,
            EntityQueryExecutor queries, List<SearchDocumentBuilder> builders, CacheTagInvalidator invalidator) {
        this.dsl = dsl;
        this.invalidator = invalidator;
        this.search = search;
        this.entities = entities;
        this.queries = queries;
        this.builders = builders.stream().collect(Collectors.toMap(SearchDocumentBuilder::entityType,
                Function.identity()));
    }

    @Override
    public String id() {
        return "search.index";
    }

    @Override
    public void run() {
        indexPending(BATCH);
    }

    /** The entity types the index holds. */
    public List<String> entityTypes() {
        return builders.keySet().stream().sorted().toList();
    }

    @EventListener
    void entityChanged(EntityEvent event) {
        boolean saved = event.phase() == EntityEvent.Phase.INSERT || event.phase() == EntityEvent.Phase.UPDATE;
        boolean deleted = event.phase() == EntityEvent.Phase.DELETE;
        if (!builders.containsKey(event.entityType()) || !(saved || deleted)
                || !(event.entity() instanceof EntityData entity)) {
            return;
        }
        long id = ((Number) entity.id()).longValue();
        if (saved) {
            markPending(event.entityType(), id);
        } else {
            dsl.deleteFrom(PENDING).where(ENTITY_TYPE.eq(event.entityType())).and(ENTITY_ID.eq(id)).execute();
            search.backend().remove(event.entityType(), id);
        }
    }

    private void markPending(String entityType, long id) {
        dsl.insertInto(PENDING).columns(ENTITY_TYPE, ENTITY_ID).values(entityType, id)
                .onConflictDoNothing().execute();
    }

    /** Marks every entity of the searchable types pending, so the next cron runs index them again. */
    public void markAllPending() {
        for (String entityType : entityTypes()) {
            queries.query(entityType).ids().forEach(id -> markPending(entityType, ((Number) id).longValue()));
        }
    }

    /**
     * Builds the index of the backend the site searches again: removes every
     * document of the searchable types from it, then indexes every entity of
     * them. Gives how many entities were indexed.
     */
    public int reindex() {
        entityTypes().forEach(entityType -> search.backend().clear(entityType));
        markAllPending();
        int indexed = 0;
        int batch;
        while ((batch = indexPending(BATCH)) > 0) {
            indexed += batch;
        }
        return indexed;
    }

    /** Makes the site search through another backend and builds that backend's index. */
    public int switchBackend(String backendId) {
        search.choose(backendId);
        return reindex();
    }

    /** The id of the backend the site searches now. */
    public String backendId() {
        return search.backend().id();
    }

    /** How many entities wait to be indexed. */
    public int pending() {
        return dsl.fetchCount(PENDING);
    }

    /** Indexes up to {@code limit} pending entities, removing those gone since they were marked. */
    public int indexPending(int limit) {
        var marked = dsl.select(ENTITY_TYPE, ENTITY_ID).from(PENDING).orderBy(ENTITY_TYPE, ENTITY_ID).limit(limit)
                .fetch();
        for (var row : marked) {
            String entityType = row.get(ENTITY_TYPE);
            long id = row.get(ENTITY_ID);
            Optional<EntityData> entity = builders.containsKey(entityType) ? entities.load(entityType, id)
                    : Optional.empty();
            if (entity.isPresent()) {
                search.backend().index(builders.get(entityType).build(entity.get()));
            } else {
                search.backend().remove(entityType, id);
            }
            dsl.deleteFrom(PENDING).where(ENTITY_TYPE.eq(entityType)).and(ENTITY_ID.eq(id)).execute();
            invalidator.invalidate(List.of(CacheTags.list(entityType)));
        }
        return marked.size();
    }
}
