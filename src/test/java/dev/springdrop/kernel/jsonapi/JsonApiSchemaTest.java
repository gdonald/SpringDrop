package dev.springdrop.kernel.jsonapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.BooleanFieldType;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.types.FloatFieldType;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.user.UserEntityType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class JsonApiSchemaTest {

    private static final EntityType NODE = NodeEntityType.definition();

    /** A type with a base field of every column type, for reading filter values. */
    private static final EntityType SAMPLE = EntityType.content("sample", EntityData.class)
            .withBaseFields(List.of(
                    BaseFieldDefinition.optional("flag", ColumnType.BOOLEAN),
                    BaseFieldDefinition.optional("big", ColumnType.BIGINT),
                    BaseFieldDefinition.optional("small", ColumnType.INTEGER),
                    BaseFieldDefinition.optional("moment", ColumnType.TIMESTAMP),
                    BaseFieldDefinition.optional("token", ColumnType.UUID),
                    BaseFieldDefinition.optional("blob", ColumnType.JSONB),
                    BaseFieldDefinition.optional("note", ColumnType.TEXT),
                    BaseFieldDefinition.optional("code", ColumnType.VARCHAR)));

    private final FieldConfigManager fields = mock(FieldConfigManager.class);

    private final JsonApiSchema schema = new JsonApiSchema(fields);

    private static FieldStorageConfig reference(String name, String target, int cardinality) {
        return new FieldStorageConfig(name, NodeEntityType.ID, EntityReferenceFieldType.ID, cardinality,
                target == null ? Map.of() : Map.of(EntityReferenceFieldType.TARGET_TYPE, target));
    }

    private static void refused(Runnable reading, String detail) {
        assertThatThrownBy(reading::run).isInstanceOfSatisfying(JsonApiException.class, refusal -> {
            assertThat(refusal.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(refusal.getMessage()).isEqualTo(detail);
        });
    }

    @Nested
    class Members {

        @BeforeEach
        void articleFields() {
            when(fields.storages(NodeEntityType.ID)).thenReturn(List.of(
                    reference("tags", "taxonomy_term", FieldStorageConfig.UNLIMITED),
                    reference("lead", "node", 1),
                    reference("broken", null, 1),
                    FieldStorageConfig.single("summary", NodeEntityType.ID, StringFieldType.ID)));
            when(fields.fieldNames(NodeEntityType.ID, "article")).thenReturn(List.of("tags", "summary"));
            when(fields.findStorage(NodeEntityType.ID, "tags"))
                    .thenReturn(Optional.of(reference("tags", "taxonomy_term", FieldStorageConfig.UNLIMITED)));
            when(fields.findStorage(NodeEntityType.ID, "lead")).thenReturn(Optional.of(reference("lead", "node", 1)));
        }

        @Test
        void theRelationshipsAreTheOwnerAndEachReferenceFieldWithATarget() {
            assertThat(schema.relationships(NODE)).containsExactly(Map.entry("owner", "user"),
                    Map.entry("tags", "taxonomy_term"), Map.entry("lead", "node"));
        }

        @Test
        void aBundleCarriesTheOwnerAndTheReferenceFieldsAttachedToIt() {
            assertThat(schema.relationships(NODE, "article")).containsOnlyKeys("owner", "tags");
        }

        @Test
        void aTypeWithoutAnOwnerHasNoOwnerRelationship() {
            assertThat(schema.relationships(UserEntityType.definition())).isEmpty();
        }

        @Test
        void theOwnerAndAOneValueFieldHoldOneEntity() {
            assertThat(schema.singleValued(NODE, "owner")).isTrue();
            assertThat(schema.singleValued(NODE, "lead")).isTrue();
            assertThat(schema.singleValued(NODE, "tags")).isFalse();
            assertThat(schema.singleValued(NODE, "gone")).isTrue();
        }

        @Test
        void theAttributesAreTheKeysAndEveryFieldThatIsNotARelationship() {
            assertThat(schema.attributes(NODE, "article")).startsWith("drupal_internal__id", "label", "langcode",
                    "status").contains("summary").doesNotContain("owner", "tags");
        }

        @Test
        void anAttributeIsStoredAsTheIdKeyOrAsItself() {
            assertThat(schema.property(NODE, "article", "drupal_internal__id")).contains("id");
            assertThat(schema.property(NODE, "article", "summary")).contains("summary");
            assertThat(schema.property(NODE, "article", "tags")).isEmpty();
        }
    }

    @Test
    void theIdLabelAndLanguageAreKeys() {
        assertThat(JsonApiSchema.isKey(NODE, "id")).isTrue();
        assertThat(JsonApiSchema.isKey(NODE, "label")).isTrue();
        assertThat(JsonApiSchema.isKey(NODE, "langcode")).isTrue();
        assertThat(JsonApiSchema.isKey(NODE, "summary")).isFalse();
    }

    @Nested
    class FilterValues {

        @Test
        void theIdIsAWholeNumber() {
            assertThat(schema.filterValue(SAMPLE, "id", "12")).isEqualTo(12L);
            refused(() -> schema.filterValue(SAMPLE, "id", "twelve"), "The filter value for id is not a whole number.");
        }

        @Test
        void aYesOrNoColumnTakesTrueFalseOneOrZero() {
            assertThat(schema.filterValue(SAMPLE, "flag", "true")).isEqualTo(true);
            assertThat(schema.filterValue(SAMPLE, "flag", "1")).isEqualTo(true);
            assertThat(schema.filterValue(SAMPLE, "flag", "false")).isEqualTo(false);
            assertThat(schema.filterValue(SAMPLE, "flag", "0")).isEqualTo(false);
            refused(() -> schema.filterValue(SAMPLE, "flag", "maybe"),
                    "The filter value for flag is not true or false.");
        }

        @Test
        void numberColumnsTakeWholeNumbersThatFit() {
            assertThat(schema.filterValue(SAMPLE, "big", "-40")).isEqualTo(-40L);
            assertThat(schema.filterValue(SAMPLE, "small", "40")).isEqualTo(40);
            refused(() -> schema.filterValue(SAMPLE, "small", "9999999999"),
                    "The filter value for small is not a whole number.");
        }

        @Test
        void aTimestampColumnTakesADateAndTimeWithAnOffset() {
            assertThat(schema.filterValue(SAMPLE, "moment", "2026-10-15T09:00:00Z"))
                    .isEqualTo(OffsetDateTime.parse("2026-10-15T09:00:00Z"));
            refused(() -> schema.filterValue(SAMPLE, "moment", "yesterday"), "The filter value for moment is not "
                    + "a date and time with an offset, such as 2026-10-15T09:00:00Z.");
        }

        @Test
        void aUuidColumnTakesAUuid() {
            UUID token = UUID.randomUUID();
            assertThat(schema.filterValue(SAMPLE, "token", token.toString())).isEqualTo(token);
            refused(() -> schema.filterValue(SAMPLE, "token", "x"), "The filter value for token is not a uuid.");
        }

        @Test
        void aStructuredColumnIsNotFilteredOn() {
            refused(() -> schema.filterValue(SAMPLE, "blob", "x"),
                    "The attribute blob cannot be filtered or sorted on.");
        }

        @Test
        void textColumnsTakeTheTextAsItIs() {
            assertThat(schema.filterValue(SAMPLE, "note", "Opening hours")).isEqualTo("Opening hours");
            assertThat(schema.filterValue(SAMPLE, "code", "A-1")).isEqualTo("A-1");
        }

        @Test
        void aConfigurableFieldTakesTheKindOfValueItsTypeHolds() {
            when(fields.findStorage("sample", "count"))
                    .thenReturn(Optional.of(FieldStorageConfig.single("count", "sample", IntegerFieldType.ID)));
            when(fields.findStorage("sample", "weight"))
                    .thenReturn(Optional.of(FieldStorageConfig.single("weight", "sample", FloatFieldType.ID)));
            when(fields.findStorage("sample", "done"))
                    .thenReturn(Optional.of(FieldStorageConfig.single("done", "sample", BooleanFieldType.ID)));
            when(fields.findStorage("sample", "title"))
                    .thenReturn(Optional.of(FieldStorageConfig.single("title", "sample", StringFieldType.ID)));

            assertThat(schema.filterValue(SAMPLE, "count", "3")).isEqualTo(3L);
            assertThat(schema.filterValue(SAMPLE, "weight", "1.5")).isEqualTo(new BigDecimal("1.5"));
            refused(() -> schema.filterValue(SAMPLE, "weight", "heavy"), "The filter value for weight is not a number.");
            assertThat(schema.filterValue(SAMPLE, "done", "1")).isEqualTo(true);
            assertThat(schema.filterValue(SAMPLE, "title", "3")).isEqualTo("3");
            assertThat(schema.filterValue(SAMPLE, "unknown", "3")).isEqualTo("3");
        }
    }
}
