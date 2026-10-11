package dev.springdrop.kernel.rest;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RestFieldValuesTest {

    private static final EntityType RECORD = EntityType.content("record", EntityData.class).withBaseFields(List.of(
            BaseFieldDefinition.optional("flag", ColumnType.BOOLEAN),
            BaseFieldDefinition.optional("count", ColumnType.INTEGER),
            BaseFieldDefinition.optional("total", ColumnType.BIGINT),
            BaseFieldDefinition.optional("code", ColumnType.VARCHAR),
            BaseFieldDefinition.optional("notes", ColumnType.TEXT),
            BaseFieldDefinition.optional("seen", ColumnType.TIMESTAMP)));

    private final RestFieldValues values = new RestFieldValues((entityType, bundle) ->
            entityType.equals("record") && bundle.equals("record") ? List.of("body") : List.of());

    @Test
    void eachBaseFieldIsReadAsItsColumnTakesIt() {
        RestFieldValues.Result result = values.writable(RECORD, null, Map.of("flag", "false", "count", 3,
                "total", "-12", "code", "A1", "notes", 7, "body", Map.of("value", "x")));

        assertThat(result.error()).isNull();
        assertThat(result.values()).containsEntry("flag", false).containsEntry("count", 3)
                .containsEntry("total", -12L).containsEntry("code", "A1").containsEntry("notes", "7")
                .containsEntry("body", Map.of("value", "x"));
    }

    @Test
    void aValueTheColumnCannotTakeIsRefused() {
        assertThat(values.writable(RECORD, null, Map.of("count", "many")).error())
                .isEqualTo("The value of count is not a whole number.");
        assertThat(values.writable(RECORD, null, Map.of("total", 1.5)).error())
                .isEqualTo("The value of total is not a whole number.");
        assertThat(values.writable(RECORD, null, Map.of("seen", "2026-01-01")).error())
                .isEqualTo("The value of seen is not a value this field takes.");
    }
}
