package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.schema.ColumnSpec;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.schema.SchemaManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The per-field tables of a content entity type. A field's current values live
 * in {@code <base table>__<field>}, keyed by entity, language, and delta, and a
 * revisionable type keeps every revision's values in
 * {@code <base table>_revision__<field>} alongside them. Values are stored as
 * {@code jsonb} to keep their type across a round trip.
 */
@Component
public class FieldTableStorage {

    public static final String ENTITY_ID = "entity_id";

    public static final String REVISION_ID = "revision_id";

    public static final String LANGCODE = "langcode";

    public static final String DELTA = "delta";

    public static final String VALUE = "value";

    private static final Field<Object> ENTITY_ID_FIELD = DSL.field(DSL.name(ENTITY_ID));
    private static final Field<Object> REVISION_ID_FIELD = DSL.field(DSL.name(REVISION_ID));
    private static final Field<Object> LANGCODE_FIELD = DSL.field(DSL.name(LANGCODE));
    private static final Field<Integer> DELTA_FIELD = DSL.field(DSL.name(DELTA), SQLDataType.INTEGER);
    private static final Field<JSONB> VALUE_FIELD = DSL.field(DSL.name(VALUE), SQLDataType.JSONB);

    private final SchemaManager schemaManager;
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public FieldTableStorage(SchemaManager schemaManager, DSLContext dsl, ObjectMapper objectMapper) {
        this.schemaManager = schemaManager;
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    /** The table holding a field's current values. */
    public static String tableName(EntityType type, String field) {
        return type.baseTable() + "__" + field;
    }

    /** The table holding a field's values for every revision. */
    public static String revisionTableName(EntityType type, String field) {
        return type.revisionTable() + "__" + field;
    }

    public void install(EntityType type, String field) {
        schemaManager.createTable(
                tableName(type, field),
                columns(false),
                List.of(ENTITY_ID, LANGCODE, DELTA));
        if (type.revisionable()) {
            schemaManager.createTable(
                    revisionTableName(type, field),
                    columns(true),
                    List.of(ENTITY_ID, REVISION_ID, LANGCODE, DELTA));
        }
    }

    public void uninstall(EntityType type, String field) {
        schemaManager.dropTable(tableName(type, field));
        if (type.revisionable()) {
            schemaManager.dropTable(revisionTableName(type, field));
        }
    }

    /**
     * Replaces the values a field holds for one entity and language, in the
     * current table and, for a revisionable type, in the revision being written.
     */
    public void write(EntityType type, Object id, Long revisionId, String langcode, String field, Object value) {
        List<?> values = (value instanceof List<?> list) ? list : List.of(value);

        writeRows(tableName(type, field), currentCondition(id, langcode), id, null, langcode, values);
        if (type.revisionable()) {
            writeRows(revisionTableName(type, field),
                    currentCondition(id, langcode).and(REVISION_ID_FIELD.eq(revisionId)),
                    id, revisionId, langcode, values);
        }
    }

    /**
     * The current values of the named fields for one entity and language. A
     * field with a single value reads back as that value, one with several reads
     * back as a list in delta order, and one with no values is left out.
     */
    public Map<String, Object> read(EntityType type, Object id, String langcode, List<String> fields) {
        return readFrom(field -> tableName(type, field), currentCondition(id, langcode), fields);
    }

    /** The values the named fields held in one revision. */
    public Map<String, Object> readRevision(
            EntityType type, Object id, Long revisionId, String langcode, List<String> fields) {

        return readFrom(
                field -> revisionTableName(type, field),
                currentCondition(id, langcode).and(REVISION_ID_FIELD.eq(revisionId)),
                fields);
    }

    /** Removes every value of every named field for one entity, in all languages and revisions. */
    public void deleteAll(EntityType type, Object id, List<String> fields) {
        for (String field : fields) {
            deleteRows(tableName(type, field), ENTITY_ID_FIELD.eq(id));
            if (type.revisionable()) {
                deleteRows(revisionTableName(type, field), ENTITY_ID_FIELD.eq(id));
            }
        }
    }

    private Map<String, Object> readFrom(
            java.util.function.Function<String, String> table, Condition where, List<String> fields) {

        Map<String, Object> values = new LinkedHashMap<>();
        for (String field : fields) {
            List<Object> fieldValues = dsl.select(VALUE_FIELD)
                    .from(DSL.table(DSL.name(table.apply(field))))
                    .where(where)
                    .orderBy(DELTA_FIELD)
                    .fetch()
                    .map(record -> objectMapper.readValue(record.get(VALUE_FIELD).data(), Object.class));

            if (fieldValues.size() == 1) {
                values.put(field, fieldValues.getFirst());
            } else if (!fieldValues.isEmpty()) {
                values.put(field, List.copyOf(fieldValues));
            }
        }
        return Map.copyOf(values);
    }

    private void writeRows(
            String table, Condition where, Object id, Long revisionId, String langcode, List<?> values) {

        deleteRows(table, where);
        for (int delta = 0; delta < values.size(); delta++) {
            Map<Field<?>, Object> row = new LinkedHashMap<>();
            row.put(ENTITY_ID_FIELD, id);
            row.put(LANGCODE_FIELD, langcode);
            row.put(DELTA_FIELD, delta);
            row.put(VALUE_FIELD, JSONB.valueOf(objectMapper.writeValueAsString(values.get(delta))));
            if (revisionId != null) {
                row.put(REVISION_ID_FIELD, revisionId);
            }
            dsl.insertInto(DSL.table(DSL.name(table))).set(row).execute();
        }
    }

    private void deleteRows(String table, Condition where) {
        dsl.deleteFrom(DSL.table(DSL.name(table))).where(where).execute();
    }

    private static Condition currentCondition(Object id, String langcode) {
        return ENTITY_ID_FIELD.eq(id).and(LANGCODE_FIELD.eq(langcode));
    }

    private static List<ColumnSpec> columns(boolean revisioned) {
        List<ColumnSpec> columns = new ArrayList<>(List.of(
                ColumnSpec.required(ENTITY_ID, ColumnType.BIGINT),
                ColumnSpec.required(LANGCODE, ColumnType.VARCHAR),
                ColumnSpec.required(DELTA, ColumnType.INTEGER),
                ColumnSpec.optional(VALUE, ColumnType.JSONB)));
        if (revisioned) {
            columns.add(ColumnSpec.required(REVISION_ID, ColumnType.BIGINT));
        }
        return columns;
    }
}
