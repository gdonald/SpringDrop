package dev.springdrop.kernel.schema;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The test module descriptors on the classpath own migrations under
 * {@code db/migration/<module>}: foundation creates a table and content, which
 * depends on foundation, references it.
 */
@SpringBootTest
class ModuleMigrationStrategyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private DSLContext dsl;

    @Test
    void aModuleMigrationIsAppliedAfterTheMigrationsOfTheModuleItDependsOn() {
        assertThat(schemaManager.tableExists("foundation_example")).isTrue();
        assertThat(schemaManager.tableExists("content_example")).isTrue();
    }

    @Test
    void eachModuleTracksItsMigrationsInItsOwnHistoryTable() {
        assertThat(schemaManager.tableExists("flyway_schema_history_foundation")).isTrue();
        assertThat(schemaManager.tableExists("flyway_schema_history_content")).isTrue();
    }

    @Test
    void aModuleWithoutMigrationsGetsNoHistoryTable() {
        assertThat(schemaManager.tableExists("flyway_schema_history_blog")).isFalse();
    }

    @Test
    void aModuleMigrationIsRecordedOnceAcrossRepeatedStartups() {
        Integer applied = dsl.selectCount()
                .from(DSL.table("flyway_schema_history_foundation"))
                .where(DSL.field("version", String.class).eq("1"))
                .fetchOne(0, Integer.class);

        assertThat(applied).isEqualTo(1);
    }
}
