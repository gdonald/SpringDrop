package dev.springdrop.kernel.schema;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FlywayBaselineTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private SchemaManager schemaManager;

    @Test
    void baselineCreatesTheCoreStores() {
        assertThat(schemaManager.tableExists("config")).isTrue();
        assertThat(schemaManager.tableExists("key_value")).isTrue();
        assertThat(schemaManager.tableExists("key_value_expire")).isTrue();
    }

    @Test
    void migratingAnAlreadyMigratedDatabaseIsANoOp() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
