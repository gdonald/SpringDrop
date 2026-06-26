package dev.springdrop.kernel.schema;

import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * Creates, alters, and drops tables at runtime through jOOQ DDL. The Field API
 * uses this to build and tear down per-field storage tables as fields are
 * created and deleted, the SpringDrop analogue of Drupal's runtime Schema API.
 * All operations are idempotent so they are safe to re-run.
 */
@Component
public class SchemaManager {

    private final DSLContext dsl;

    public SchemaManager(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void createTable(String table, List<ColumnSpec> columns, String primaryKey) {
        Field<?>[] fields = columns.stream()
                .map(column -> DSL.field(DSL.name(column.name()), column.dataType()))
                .toArray(Field[]::new);

        dsl.createTableIfNotExists(DSL.name(table))
                .columns(fields)
                .constraints(DSL.primaryKey(DSL.name(primaryKey)))
                .execute();
    }

    public void addColumn(String table, ColumnSpec column) {
        dsl.alterTable(DSL.name(table))
                .addColumnIfNotExists(DSL.field(DSL.name(column.name()), column.dataType()))
                .execute();
    }

    public void addForeignKey(String table, String column, String referencedTable, String referencedColumn) {
        dsl.alterTable(DSL.name(table))
                .add(DSL.constraint(DSL.name(table + "_" + column + "_fkey"))
                        .foreignKey(DSL.name(column))
                        .references(DSL.name(referencedTable), DSL.name(referencedColumn)))
                .execute();
    }

    public void createIndex(String indexName, String table, List<String> columns, String whereCondition) {
        Field<?>[] fields = columns.stream()
                .map(name -> DSL.field(DSL.name(name)))
                .toArray(Field[]::new);

        var onStep = dsl.createIndexIfNotExists(DSL.name(indexName))
                .on(DSL.table(DSL.name(table)), fields);

        if (whereCondition == null) {
            onStep.execute();
        } else {
            onStep.where(DSL.condition(whereCondition)).execute();
        }
    }

    public void dropTable(String table) {
        dsl.dropTableIfExists(DSL.name(table)).execute();
    }

    public boolean tableExists(String table) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(DSL.table("information_schema.tables"))
                        .where(DSL.field("table_schema").eq("public"))
                        .and(DSL.field("table_name").eq(table)));
    }

    public boolean columnExists(String table, String column) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(DSL.table("information_schema.columns"))
                        .where(DSL.field("table_schema").eq("public"))
                        .and(DSL.field("table_name").eq(table))
                        .and(DSL.field("column_name").eq(column)));
    }
}
