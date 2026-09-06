package dev.springdrop.kernel.entity.query;

import dev.springdrop.kernel.entity.EntityKeys;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.FieldTableStorage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns an {@link EntityQuery} into SQL and runs it. A condition on a base key
 * reads the base table; a condition on a field joins that field's table, keyed
 * by entity and narrowed to the query's language and revision. A tagged query is
 * published as an {@link EntityQueryAlterEvent} first, so the access system can
 * add conditions before anything runs.
 */
@Component
public class EntityQueryExecutor {

    private final EntityTypeManager entityTypeManager;
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public EntityQueryExecutor(
            EntityTypeManager entityTypeManager,
            DSLContext dsl,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events) {
        this.entityTypeManager = entityTypeManager;
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public EntityQuery query(String entityTypeId) {
        return new EntityQuery(entityTypeId, this);
    }

    List<Object> ids(EntityQuery query) {
        EntityType type = prepare(query);
        Field<Object> idField = baseField(type, type.keys().id());

        SelectJoinStep<Record1<Object>> select = dsl.select(idField).from(baseTable(type));
        joinFieldTables(select, type, query);

        List<SortField<?>> sorts = new ArrayList<>();
        for (Sort sort : query.sorts()) {
            Field<?> field = propertyField(type, query, sort.property());
            sorts.add(sort.ascending() ? field.asc() : field.desc());
        }

        var withConditions = select.where(whereClause(type, query)).orderBy(sorts);
        if (query.limit() >= 0) {
            return List.copyOf(withConditions.offset(query.offset()).limit(query.limit()).fetch(idField));
        }
        return List.copyOf(withConditions.fetch(idField));
    }

    long count(EntityQuery query) {
        EntityType type = prepare(query);

        SelectJoinStep<Record1<Integer>> select = dsl.selectCount().from(baseTable(type));
        joinFieldTables(select, type, query);

        return select.where(whereClause(type, query)).fetchOne(0, Long.class);
    }

    private EntityType prepare(EntityQuery query) {
        if (!query.accessTags().isEmpty()) {
            events.publishEvent(new EntityQueryAlterEvent(query));
        }
        return entityTypeManager.require(query.entityTypeId());
    }

    private void joinFieldTables(SelectJoinStep<?> select, EntityType type, EntityQuery query) {
        for (String field : fieldProperties(type, query)) {
            String table = fieldTable(type, field, query);
            org.jooq.Condition on = DSL.field(DSL.name(table, FieldTableStorage.ENTITY_ID))
                    .eq(baseField(type, type.keys().id()));
            if (query.langcode() != null) {
                on = on.and(DSL.field(DSL.name(table, FieldTableStorage.LANGCODE)).eq(query.langcode()));
            }
            if (query.revisionId() != null) {
                on = on.and(DSL.field(DSL.name(table, FieldTableStorage.REVISION_ID)).eq(query.revisionId()));
            }
            select.leftJoin(DSL.table(DSL.name(table))).on(on);
        }
    }

    /** A query for one revision reads the revision tables; otherwise the current values. */
    private static String fieldTable(EntityType type, String field, EntityQuery query) {
        return (query.revisionId() == null)
                ? FieldTableStorage.tableName(type, field)
                : FieldTableStorage.revisionTableName(type, field);
    }

    private Set<String> fieldProperties(EntityType type, EntityQuery query) {
        Set<String> properties = new java.util.LinkedHashSet<>();
        for (Condition condition : query.conditions()) {
            collectProperties(condition, properties);
        }
        query.sorts().forEach(sort -> properties.add(sort.property()));
        properties.removeIf(property -> isBaseColumn(type, property));
        return properties;
    }

    private static void collectProperties(Condition condition, Set<String> properties) {
        switch (condition) {
            case Condition.Comparison comparison -> properties.add(comparison.property());
            case Condition.Group group -> group.conditions()
                    .forEach(nested -> collectProperties(nested, properties));
        }
    }

    private org.jooq.Condition whereClause(EntityType type, EntityQuery query) {
        org.jooq.Condition clause = DSL.noCondition();
        for (Condition condition : query.conditions()) {
            clause = clause.and(toJooq(type, query, condition));
        }
        return clause;
    }

    private org.jooq.Condition toJooq(EntityType type, EntityQuery query, Condition condition) {
        return switch (condition) {
            case Condition.Comparison comparison -> comparison(type, query, comparison);
            case Condition.Group group -> group(type, query, group);
        };
    }

    private org.jooq.Condition group(EntityType type, EntityQuery query, Condition.Group group) {
        List<org.jooq.Condition> parts = group.conditions().stream()
                .map(condition -> toJooq(type, query, condition))
                .toList();
        return (group.conjunction() == Condition.Conjunction.OR) ? DSL.or(parts) : DSL.and(parts);
    }

    private org.jooq.Condition comparison(EntityType type, EntityQuery query, Condition.Comparison comparison) {
        boolean base = isBaseColumn(type, comparison.property());
        Field<Object> field = propertyField(type, query, comparison.property());
        Object value = base ? comparison.value() : jsonb(comparison.value());

        return switch (comparison.operator()) {
            case EQUAL -> field.eq(value);
            case NOT_EQUAL -> field.ne(value);
            case GREATER_THAN -> field.gt(value);
            case LESS_THAN -> field.lt(value);
            case IN -> field.in(((List<?>) comparison.value()).stream()
                    .map(item -> base ? item : jsonb(item))
                    .toList());
            case CONTAINS -> textOf(type, query, comparison.property()).like("%" + comparison.value() + "%");
        };
    }

    private Field<Object> propertyField(EntityType type, EntityQuery query, String property) {
        if (isBaseColumn(type, property)) {
            return baseField(type, property);
        }
        return fieldValueColumn(type, query, property).coerce(Object.class);
    }

    /** The field's stored value as text, for substring matching. */
    private Field<String> textOf(EntityType type, EntityQuery query, String property) {
        if (isBaseColumn(type, property)) {
            return baseField(type, property).cast(SQLDataType.VARCHAR);
        }
        return DSL.field("{0} #>> '{}'", SQLDataType.VARCHAR, fieldValueColumn(type, query, property));
    }

    private static Field<JSONB> fieldValueColumn(EntityType type, EntityQuery query, String property) {
        return DSL.field(
                DSL.name(fieldTable(type, property, query), FieldTableStorage.VALUE),
                SQLDataType.JSONB);
    }

    private static Field<Object> baseField(EntityType type, String column) {
        return DSL.field(DSL.name(type.baseTable(), column));
    }

    private static Table<?> baseTable(EntityType type) {
        return DSL.table(DSL.name(type.baseTable()));
    }

    /**
     * Whether a property lives in the base table: one of the type's keys, or one
     * of the fields it defines in code. Everything else is a configured field
     * with a table of its own.
     */
    private static boolean isBaseColumn(EntityType type, String property) {
        EntityKeys keys = type.keys();
        Set<String> columns = new HashSet<>();
        columns.add(keys.id());
        columns.add(keys.uuid());
        columns.add(keys.label());
        columns.add(keys.bundle());
        columns.add(keys.langcode());
        columns.add(keys.revision());
        type.baseFields().stream().map(baseField -> baseField.name()).forEach(columns::add);
        return columns.contains(property);
    }

    private JSONB jsonb(Object value) {
        return JSONB.valueOf(objectMapper.writeValueAsString(value));
    }
}
