package dev.springdrop.kernel.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.field.types.ListFloatFieldType;
import dev.springdrop.kernel.field.types.ListIntegerFieldType;
import dev.springdrop.kernel.field.types.ListStringFieldType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class ListFieldTypeIntegrationTest extends AbstractIntegrationTest {

    record Survey(long id, String label) {
    }

    record SurveyType(String id, String label) {
    }

    static final EntityType SURVEY = EntityType.content("survey", Survey.class)
            .withBundles("type", "survey_type");

    static final EntityType SURVEY_TYPE = EntityType.config("survey_type", SurveyType.class);

    private static final String BUNDLE = "response";

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void aBundleToAttachListFieldsTo() {
        entityTypeManager.installStorage("survey");
        bundleManager.save("survey", new BundleDefinition(BUNDLE, "Response"));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("survey", BUNDLE).forEach(field -> fields.deleteStorage("survey", field));
        bundleManager.delete("survey", BUNDLE);
    }

    private void attachList(String field, String fieldTypeId, List<AllowedValue> options) {
        fields.createStorage(new FieldStorageConfig(field, "survey", fieldTypeId, 1,
                Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(options))));
        fields.createInstance(FieldInstanceConfig.of(field, "survey", BUNDLE, field));
    }

    private void save(long id, Map<String, Object> values) {
        entities.save(EntityData.of("survey", id, BUNDLE, "A response", values));
    }

    private Map<String, Object> loadedFields(long id) {
        return entities.load("survey", id).orElseThrow().fields();
    }

    @Test
    void aTextOptionFromTheAllowedSetPersists() {
        attachList("mood", ListStringFieldType.ID,
                List.of(new AllowedValue("happy", "Happy"), new AllowedValue("sad", "Sad")));

        save(1L, Map.of("mood", "happy"));

        assertThat(loadedFields(1L)).containsEntry("mood", "happy");
    }

    @Test
    void aTextOptionOutsideTheAllowedSetIsRejected() {
        attachList("mood", ListStringFieldType.ID,
                List.of(new AllowedValue("happy", "Happy"), new AllowedValue("sad", "Sad")));

        assertThatThrownBy(() -> save(2L, Map.of("mood", "indifferent")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aWholeNumberOptionFromTheAllowedSetPersists() {
        attachList("rating", ListIntegerFieldType.ID,
                List.of(new AllowedValue(1, "Poor"), new AllowedValue(5, "Excellent")));

        save(3L, Map.of("rating", 5));

        assertThat(loadedFields(3L)).containsEntry("rating", 5);
    }

    @Test
    void aWholeNumberOptionOutsideTheAllowedSetIsRejected() {
        attachList("rating", ListIntegerFieldType.ID,
                List.of(new AllowedValue(1, "Poor"), new AllowedValue(5, "Excellent")));

        assertThatThrownBy(() -> save(4L, Map.of("rating", 3)))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aNumberOptionFromTheAllowedSetPersists() {
        attachList("weighting", ListFloatFieldType.ID,
                List.of(new AllowedValue(0.5, "Half"), new AllowedValue(1.0, "Full")));

        save(5L, Map.of("weighting", 0.5));

        assertThat(loadedFields(5L)).containsEntry("weighting", 0.5);
    }

    @Test
    void aFieldWithNoAllowedValuesAcceptsNothing() {
        fields.createStorage(FieldStorageConfig.single("unset", "survey", ListStringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of("unset", "survey", BUNDLE, "Unset"));

        assertThatThrownBy(() -> save(6L, Map.of("unset", "anything")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void everyListTypeDeclaresItsWidgetAndFormatter() {
        List<String> ids = List.of(ListStringFieldType.ID, ListIntegerFieldType.ID, ListFloatFieldType.ID);

        assertThat(ids).allSatisfy(id -> {
            FieldType type = fields.fieldType(id);
            assertThat(type.id()).isEqualTo(id);
            assertThat(type.properties()).isNotEmpty();
            assertThat(type.defaultWidget()).isEqualTo("options_select");
            assertThat(type.defaultFormatter()).isEqualTo("list_default");
            assertThat(type.defaultStorageSettings()).containsKey(FieldSettings.ALLOWED_VALUES);
            assertThat(type.defaultInstanceSettings()).isEmpty();
        });
    }

    @TestConfiguration
    static class SurveyTypes {

        @Bean
        EntityTypeProvider surveyTypes() {
            return () -> List.of(SURVEY, SURVEY_TYPE);
        }
    }
}
