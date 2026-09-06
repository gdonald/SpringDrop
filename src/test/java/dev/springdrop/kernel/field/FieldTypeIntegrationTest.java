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
import dev.springdrop.kernel.field.types.BooleanFieldType;
import dev.springdrop.kernel.field.types.DecimalFieldType;
import dev.springdrop.kernel.field.types.FloatFieldType;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.StringLongFieldType;
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
class FieldTypeIntegrationTest extends AbstractIntegrationTest {

    record Measurement(long id, String label) {
    }

    record MeasurementType(String id, String label) {
    }

    static final EntityType MEASUREMENT = EntityType.content("measurement", Measurement.class)
            .withBundles("type", "measurement_type");

    static final EntityType MEASUREMENT_TYPE = EntityType.config("measurement_type", MeasurementType.class);

    private static final String BUNDLE = "reading";

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void aBundleWithOneFieldOfEachType() {
        entityTypeManager.installStorage("measurement");
        bundleManager.save("measurement", new BundleDefinition(BUNDLE, "Reading"));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("measurement", BUNDLE).forEach(field -> fields.deleteStorage("measurement", field));
        bundleManager.delete("measurement", BUNDLE);
    }

    private void attach(FieldStorageConfig storage, FieldInstanceConfig instance) {
        fields.createStorage(storage);
        fields.createInstance(instance);
    }

    private void attach(String field, String fieldTypeId) {
        attach(FieldStorageConfig.single(field, "measurement", fieldTypeId),
                FieldInstanceConfig.of(field, "measurement", BUNDLE, field));
    }

    private void save(long id, Map<String, Object> values) {
        entities.save(EntityData.of("measurement", id, BUNDLE, "A reading", values));
    }

    private Map<String, Object> loadedFields(long id) {
        return entities.load("measurement", id).orElseThrow().fields();
    }

    @Test
    void aFieldTypeDeclaresItsPropertiesAndDefaults() {
        FieldType string = fields.fieldType(StringFieldType.ID);

        assertThat(string.properties()).singleElement()
                .satisfies(property -> assertThat(property.name()).isEqualTo(FieldProperty.VALUE));
        assertThat(string.defaultWidget()).isEqualTo("string_textfield");
        assertThat(string.defaultFormatter()).isEqualTo("string");
        assertThat(string.defaultStorageSettings())
                .containsEntry(FieldSettings.MAX_LENGTH, StringFieldType.DEFAULT_MAX_LENGTH);
        assertThat(string.defaultInstanceSettings()).isEmpty();
    }

    @Test
    void aFieldStorageMustNameAFieldTypeThatExists() {
        FieldStorageConfig unknownType = FieldStorageConfig.single("mystery", "measurement", "no_such_type");

        assertThatThrownBy(() -> fields.createStorage(unknownType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no_such_type");
    }

    @Test
    void aStringFieldStoresAValueWithinItsMaximumLength() {
        attach("title", StringFieldType.ID);

        save(1L, Map.of("title", "A short title"));

        assertThat(loadedFields(1L)).containsEntry("title", "A short title");
    }

    @Test
    void aStringFieldRejectsAValueOverItsMaximumLength() {
        attach(new FieldStorageConfig("code", "measurement", StringFieldType.ID, 1,
                        Map.of(FieldSettings.MAX_LENGTH, 4)),
                FieldInstanceConfig.of("code", "measurement", BUNDLE, "Code"));

        assertThatThrownBy(() -> save(2L, Map.of("code", "far too long")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aLongStringFieldStoresTextOfAnyLength() {
        attach("body", StringLongFieldType.ID);

        save(3L, Map.of("body", "A paragraph ".repeat(100)));

        assertThat(loadedFields(3L).get("body").toString()).hasSizeGreaterThan(255);
    }

    @Test
    void anIntegerFieldStoresAValueAtItsBounds() {
        attach(FieldStorageConfig.single("count", "measurement", IntegerFieldType.ID),
                FieldInstanceConfig.of("count", "measurement", BUNDLE, "Count")
                        .withSettings(Map.of(FieldSettings.MIN, 1, FieldSettings.MAX, 10)));

        save(4L, Map.of("count", 10));

        assertThat(loadedFields(4L)).containsEntry("count", 10);
    }

    @Test
    void anIntegerFieldRejectsAValueOutsideItsBounds() {
        attach(FieldStorageConfig.single("count", "measurement", IntegerFieldType.ID),
                FieldInstanceConfig.of("count", "measurement", BUNDLE, "Count")
                        .withSettings(Map.of(FieldSettings.MIN, 1, FieldSettings.MAX, 10)));

        assertThatThrownBy(() -> save(5L, Map.of("count", 11)))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void anIntegerFieldWithoutBoundsTakesAnyWholeNumber() {
        attach("population", IntegerFieldType.ID);

        save(6L, Map.of("population", 8_000_000));

        assertThat(loadedFields(6L)).containsEntry("population", 8_000_000);
    }

    @Test
    void aDecimalFieldStoresAnExactNumber() {
        attach("price", DecimalFieldType.ID);

        save(7L, Map.of("price", 19.99));

        assertThat(loadedFields(7L)).containsEntry("price", 19.99);
        assertThat(fields.fieldType(DecimalFieldType.ID).defaultStorageSettings())
                .containsEntry(FieldSettings.PRECISION, DecimalFieldType.DEFAULT_PRECISION)
                .containsEntry(FieldSettings.SCALE, DecimalFieldType.DEFAULT_SCALE);
    }

    @Test
    void aFloatFieldStoresAnApproximateNumber() {
        attach("temperature", FloatFieldType.ID);

        save(8L, Map.of("temperature", 21.5));

        assertThat(loadedFields(8L)).containsEntry("temperature", 21.5);
    }

    @Test
    void aBooleanFieldStoresYesAndNoWithLabelsForEditors() {
        attach("published", BooleanFieldType.ID);

        save(9L, Map.of("published", true));

        assertThat(loadedFields(9L)).containsEntry("published", true);
        assertThat(fields.fieldType(BooleanFieldType.ID).defaultInstanceSettings())
                .containsEntry(FieldSettings.ON_LABEL, "On")
                .containsEntry(FieldSettings.OFF_LABEL, "Off");
    }

    @Test
    void aBooleanFieldRejectsAValueThatIsNeitherYesNorNo() {
        attach("published", BooleanFieldType.ID);

        assertThatThrownBy(() -> save(10L, Map.of("published", "maybe")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void everyScalarTypeIsRegisteredAndDeclaresItsStorageAndDisplayDefaults() {
        List<String> ids = List.of(StringFieldType.ID, StringLongFieldType.ID, IntegerFieldType.ID,
                DecimalFieldType.ID, FloatFieldType.ID, BooleanFieldType.ID);

        assertThat(ids).allSatisfy(id -> {
            FieldType type = fields.fieldType(id);
            assertThat(type.id()).isEqualTo(id);
            assertThat(type.properties()).isNotEmpty();
            assertThat(type.defaultWidget()).isNotBlank();
            assertThat(type.defaultFormatter()).isNotBlank();
            assertThat(type.defaultStorageSettings()).isNotNull();
            assertThat(type.defaultInstanceSettings()).isNotNull();
            assertThat(type.defaultConstraints(
                    FieldStorageConfig.single("probe", "measurement", id),
                    FieldInstanceConfig.of("probe", "measurement", BUNDLE, "Probe"))).isNotNull();
        });
    }

    @Test
    void aFloatFieldRejectsAValueOutsideItsBounds() {
        attach(FieldStorageConfig.single("temperature", "measurement", FloatFieldType.ID),
                FieldInstanceConfig.of("temperature", "measurement", BUNDLE, "Temperature")
                        .withSettings(Map.of(FieldSettings.MAX, 40)));

        assertThatThrownBy(() -> save(11L, Map.of("temperature", 55.5)))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDecimalFieldRejectsAValueUnderItsMinimum() {
        attach(FieldStorageConfig.single("price", "measurement", DecimalFieldType.ID),
                FieldInstanceConfig.of("price", "measurement", BUNDLE, "Price")
                        .withSettings(Map.of(FieldSettings.MIN, 0)));

        assertThatThrownBy(() -> save(12L, Map.of("price", -1.5)))
                .isInstanceOf(EntityValidationException.class);
    }

    @TestConfiguration
    static class MeasurementTypes {

        @Bean
        EntityTypeProvider measurementTypes() {
            return () -> List.of(MEASUREMENT, MEASUREMENT_TYPE);
        }
    }
}
