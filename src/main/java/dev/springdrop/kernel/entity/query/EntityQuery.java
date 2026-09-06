package dev.springdrop.kernel.entity.query;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A query for entities of one type, built up a clause at a time and run by an
 * {@link EntityQueryExecutor}. Conditions name base keys or field names alike;
 * the executor joins whatever tables the named properties live in. Access tags
 * let the access system alter the query before it runs.
 */
public class EntityQuery {

    private final String entityTypeId;
    private final EntityQueryExecutor executor;
    private final List<Condition> conditions = new ArrayList<>();
    private final List<Sort> sorts = new ArrayList<>();
    private final Set<String> accessTags = new LinkedHashSet<>();
    private String langcode;
    private Long revisionId;
    private int offset;
    private int limit = -1;

    EntityQuery(String entityTypeId, EntityQueryExecutor executor) {
        this.entityTypeId = entityTypeId;
        this.executor = executor;
    }

    public EntityQuery condition(Condition condition) {
        conditions.add(condition);
        return this;
    }

    public EntityQuery sort(Sort sort) {
        sorts.add(sort);
        return this;
    }

    public EntityQuery range(int newOffset, int newLimit) {
        this.offset = newOffset;
        this.limit = newLimit;
        return this;
    }

    /** Restricts field conditions to one language. */
    public EntityQuery inLanguage(String newLangcode) {
        this.langcode = newLangcode;
        return this;
    }

    /** Restricts field conditions to one revision. */
    public EntityQuery inRevision(Long newRevisionId) {
        this.revisionId = newRevisionId;
        return this;
    }

    /** Marks the query for the access system, which may add conditions of its own. */
    public EntityQuery accessTag(String tag) {
        accessTags.add(tag);
        return this;
    }

    public List<Object> ids() {
        return executor.ids(this);
    }

    public long count() {
        return executor.count(this);
    }

    public String entityTypeId() {
        return entityTypeId;
    }

    public List<Condition> conditions() {
        return List.copyOf(conditions);
    }

    public List<Sort> sorts() {
        return List.copyOf(sorts);
    }

    public Set<String> accessTags() {
        return Set.copyOf(accessTags);
    }

    public String langcode() {
        return langcode;
    }

    public Long revisionId() {
        return revisionId;
    }

    public int offset() {
        return offset;
    }

    public int limit() {
        return limit;
    }
}
