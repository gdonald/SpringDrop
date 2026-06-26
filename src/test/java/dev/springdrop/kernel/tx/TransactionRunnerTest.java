package dev.springdrop.kernel.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.schema.ColumnSpec;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransactionRunnerTest extends AbstractIntegrationTest {

    private static final String TABLE = "tx_runner_probe";

    @Autowired
    private TransactionRunner transactionRunner;

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        schemaManager.dropTable(TABLE);
        schemaManager.createTable(TABLE, List.of(ColumnSpec.required("id", ColumnType.BIGINT)), "id");
    }

    @AfterEach
    void tearDown() {
        schemaManager.dropTable(TABLE);
    }

    @Test
    void runsWorkInsideATransaction() {
        transactionRunner.run(() ->
                dsl.insertInto(DSL.table(DSL.name(TABLE)))
                        .columns(DSL.field(DSL.name("id")))
                        .values(3L)
                        .execute());

        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void committedWorkPersists() {
        long inserted = transactionRunner.call(() ->
                (long) dsl.insertInto(DSL.table(DSL.name(TABLE)))
                        .columns(DSL.field(DSL.name("id")))
                        .values(1L)
                        .execute());

        assertThat(inserted).isEqualTo(1L);
        assertThat(rowCount()).isEqualTo(1);
    }

    @Test
    void aFailureMidWorkRollsBackEveryWrite() {
        assertThatThrownBy(() -> transactionRunner.run(() -> {
            dsl.insertInto(DSL.table(DSL.name(TABLE)))
                    .columns(DSL.field(DSL.name("id")))
                    .values(2L)
                    .execute();
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(rowCount()).isZero();
    }

    private int rowCount() {
        return dsl.fetchCount(DSL.table(DSL.name(TABLE)));
    }
}
