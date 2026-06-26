package dev.springdrop.kernel.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SchemaManagerTest extends AbstractIntegrationTest {

    private static final String TABLE = "schema_manager_probe";
    private static final String PARENT_TABLE = "schema_manager_parent";

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    @AfterEach
    void clean() {
        schemaManager.dropTable(TABLE);
        schemaManager.dropTable(PARENT_TABLE);
    }

    @Test
    void createsIndexesAltersAndDropsATableIdempotently() {
        assertThat(schemaManager.tableExists(TABLE)).isFalse();

        List<ColumnSpec> columns = List.of(
                ColumnSpec.required("id", ColumnType.BIGINT),
                ColumnSpec.required("payload", ColumnType.JSONB),
                ColumnSpec.optional("published", ColumnType.BOOLEAN));
        schemaManager.createTable(TABLE, columns, "id");
        // Re-running create is a no-op rather than an error.
        schemaManager.createTable(TABLE, columns, "id");

        assertThat(schemaManager.tableExists(TABLE)).isTrue();
        assertThat(schemaManager.columnExists(TABLE, "payload")).isTrue();
        assertThat(schemaManager.columnExists(TABLE, "langcode")).isFalse();

        schemaManager.addColumn(TABLE, ColumnSpec.optional("langcode", ColumnType.VARCHAR));
        assertThat(schemaManager.columnExists(TABLE, "langcode")).isTrue();

        // A partial index over published rows, the published-content listing pattern.
        schemaManager.createIndex("schema_manager_probe_published", TABLE, List.of("id"), "published = true");
        // A plain index with no predicate.
        schemaManager.createIndex("schema_manager_probe_langcode", TABLE, List.of("langcode"), null);

        schemaManager.dropTable(TABLE);
        assertThat(schemaManager.tableExists(TABLE)).isFalse();
    }

    @Test
    void enforcesForeignKeyConstraints() {
        schemaManager.createTable(PARENT_TABLE, List.of(ColumnSpec.required("id", ColumnType.BIGINT)), "id");
        schemaManager.createTable(
                TABLE,
                List.of(ColumnSpec.required("id", ColumnType.BIGINT), ColumnSpec.required("parent_id", ColumnType.BIGINT)),
                "id");
        schemaManager.addForeignKey(TABLE, "parent_id", PARENT_TABLE, "id");

        assertThatThrownBy(() ->
                dsl.insertInto(DSL.table(DSL.name(TABLE)))
                        .columns(DSL.field(DSL.name("id")), DSL.field(DSL.name("parent_id")))
                        .values(1L, 999L)
                        .execute())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
