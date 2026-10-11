package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.schema.ColumnSpec;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.schema.SchemaManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Stores content entities in their own tables: a base table holding one row per
 * entity, and a revision table alongside it for a revisionable type. Field
 * tables are created by the Field API and joined in when fields land.
 */
@Component
public class ContentEntityStorage implements EntityStorage {

    private final SchemaManager schemaManager;
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public ContentEntityStorage(SchemaManager schemaManager, DSLContext dsl, ObjectMapper objectMapper) {
        this.schemaManager = schemaManager;
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    /**
     * The value as its column takes it. A base field stored as {@code jsonb}
     * holds whatever shape it was given, so lists and maps are written as JSON
     * rather than handed to the driver as Java objects.
     */
    public Object columnValue(EntityType type, String column, Object value) {
        return isJson(type, column) ? JSONB.valueOf(objectMapper.writeValueAsString(value)) : value;
    }

    /** The value as the rest of the code reads it, JSON parsed back into shape. */
    public Object javaValue(EntityType type, String column, Object stored) {
        if (!isJson(type, column) || !(stored instanceof JSONB json)) {
            return stored;
        }
        return objectMapper.readValue(json.data(), Object.class);
    }

    private static boolean isJson(EntityType type, String column) {
        return type.baseFields().stream()
                .anyMatch(field -> field.name().equals(column) && field.type() == ColumnType.JSONB);
    }

    @Override
    public void install(EntityType type) {
        EntityKeys keys = type.keys();
        schemaManager.createTable(type.baseTable(), baseColumns(type), keys.id());
        if (type.revisionable()) {
            schemaManager.createTable(type.revisionTable(), revisionColumns(type), keys.revision());
        }
        if (type.translatable()) {
            schemaManager.createTable(type.translationTable(), List.of(
                    ColumnSpec.required(keys.id(), ColumnType.BIGINT),
                    ColumnSpec.required(keys.langcode(), ColumnType.VARCHAR),
                    ColumnSpec.optional(keys.label(), ColumnType.VARCHAR)), List.of(keys.id(), keys.langcode()));
        }
    }

    @Override
    public void uninstall(EntityType type) {
        if (type.translatable()) {
            schemaManager.dropTable(type.translationTable());
        }
        if (type.revisionable()) {
            schemaManager.dropTable(type.revisionTable());
        }
        schemaManager.dropTable(type.baseTable());
    }

    @Override
    public Object nextId(EntityType type) {
        Long highest = dsl.select(DSL.max(DSL.field(DSL.name(type.keys().id()), Long.class)))
                .from(DSL.table(DSL.name(type.baseTable())))
                .fetchOne(0, Long.class);
        return (highest == null) ? 1L : highest + 1;
    }

    @Override
    public Optional<Map<String, Object>> load(EntityType type, Object id) {
        Record row = dsl.select()
                .from(DSL.table(DSL.name(type.baseTable())))
                .where(idField(type).eq(id))
                .fetchOne();
        return Optional.ofNullable(row).map(found -> found.intoMap());
    }

    @Override
    public void save(EntityType type, Object id, Map<String, Object> values) {
        Map<Field<?>, Object> row = new LinkedHashMap<>();
        row.put(idField(type), id);
        values.forEach((column, value) ->
                row.put(DSL.field(DSL.name(column)), columnValue(type, column, value)));

        dsl.insertInto(DSL.table(DSL.name(type.baseTable())))
                .set(row)
                .onConflict(idField(type))
                .doUpdate()
                .set(row)
                .execute();
    }

    @Override
    public void delete(EntityType type, Object id) {
        if (type.translatable()) {
            dsl.deleteFrom(DSL.table(DSL.name(type.translationTable())))
                    .where(DSL.field(DSL.name(type.keys().id())).eq(id))
                    .execute();
        }
        if (type.revisionable()) {
            dsl.deleteFrom(DSL.table(DSL.name(type.revisionTable())))
                    .where(DSL.field(DSL.name(type.keys().id())).eq(id))
                    .execute();
        }
        dsl.deleteFrom(DSL.table(DSL.name(type.baseTable())))
                .where(idField(type).eq(id))
                .execute();
    }

    /** The label of the entity's translation into the language, when it has one. */
    public Optional<String> translationLabel(EntityType type, Object id, String langcode) {
        return dsl.select(DSL.field(DSL.name(type.keys().label()), String.class))
                .from(DSL.table(DSL.name(type.translationTable())))
                .where(idField(type).eq(id)).and(DSL.field(DSL.name(type.keys().langcode())).eq(langcode))
                .fetchOptional()
                .map(record -> String.valueOf(record.value1()));
    }

    /** The languages the entity is translated into, apart from its own, by code. */
    public List<String> translationLangcodes(EntityType type, Object id) {
        Field<String> langcode = DSL.field(DSL.name(type.keys().langcode()), String.class);
        return dsl.select(langcode).from(DSL.table(DSL.name(type.translationTable())))
                .where(idField(type).eq(id)).orderBy(langcode).fetch(langcode);
    }

    public void saveTranslation(EntityType type, Object id, String langcode, String label) {
        Field<Object> langcodeField = DSL.field(DSL.name(type.keys().langcode()));
        Field<Object> labelField = DSL.field(DSL.name(type.keys().label()));
        dsl.insertInto(DSL.table(DSL.name(type.translationTable())))
                .set(idField(type), id).set(langcodeField, langcode).set(labelField, label)
                .onConflict(idField(type), langcodeField).doUpdate().set(labelField, (Object) label)
                .execute();
    }

    public void deleteTranslation(EntityType type, Object id, String langcode) {
        dsl.deleteFrom(DSL.table(DSL.name(type.translationTable())))
                .where(idField(type).eq(id)).and(DSL.field(DSL.name(type.keys().langcode())).eq(langcode))
                .execute();
    }

    private static Field<Object> idField(EntityType type) {
        return DSL.field(DSL.name(type.keys().id()));
    }

    private static List<ColumnSpec> baseColumns(EntityType type) {
        EntityKeys keys = type.keys();
        List<ColumnSpec> columns = new ArrayList<>();
        columns.add(ColumnSpec.required(keys.id(), ColumnType.BIGINT));
        columns.add(ColumnSpec.required(keys.uuid(), ColumnType.UUID));
        columns.add(ColumnSpec.optional(keys.label(), ColumnType.VARCHAR));
        if (keys.bundle() != null) {
            columns.add(ColumnSpec.required(keys.bundle(), ColumnType.VARCHAR));
        }
        if (type.translatable()) {
            columns.add(ColumnSpec.required(keys.langcode(), ColumnType.VARCHAR));
        }
        if (type.revisionable()) {
            columns.add(ColumnSpec.optional(keys.revision(), ColumnType.BIGINT));
        }
        columns.addAll(baseFieldColumns(type));
        return columns;
    }

    private static List<ColumnSpec> revisionColumns(EntityType type) {
        EntityKeys keys = type.keys();
        List<ColumnSpec> columns = new ArrayList<>();
        columns.add(ColumnSpec.required(keys.revision(), ColumnType.BIGINT));
        columns.add(ColumnSpec.required(keys.id(), ColumnType.BIGINT));
        columns.add(ColumnSpec.optional(keys.label(), ColumnType.VARCHAR));
        if (type.translatable()) {
            columns.add(ColumnSpec.required(keys.langcode(), ColumnType.VARCHAR));
        }
        columns.addAll(baseFieldColumns(type));
        return columns;
    }

    private static List<ColumnSpec> baseFieldColumns(EntityType type) {
        return type.baseFields().stream()
                .map(field -> new ColumnSpec(field.name(), field.type(), !field.required()))
                .toList();
    }
}
