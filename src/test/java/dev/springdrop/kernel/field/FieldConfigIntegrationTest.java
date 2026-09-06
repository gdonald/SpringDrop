package dev.springdrop.kernel.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.FieldTableStorage;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.schema.SchemaManager;
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
class FieldConfigIntegrationTest extends AbstractIntegrationTest {

    record Recipe(long id, String label) {
    }

    record RecipeType(String id, String label) {
    }

    static final EntityType RECIPE = EntityType.content("recipe", Recipe.class)
            .withBundles("type", "recipe_type")
            .withRevisions()
            .withBaseFields(BaseFieldDefinition.authoredContent());

    static final EntityType RECIPE_TYPE = EntityType.config("recipe_type", RecipeType.class);

    /** A type with a base field its storage must always hold. */
    static final EntityType PORTION = EntityType.content("portion", Recipe.class)
            .withBaseFields(List.of(BaseFieldDefinition.required("amount", ColumnType.INTEGER)));

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private SchemaManager schemaManager;

    @BeforeEach
    void twoBundlesSharingOneFieldStorage() {
        entityTypeManager.installStorage("recipe");
        bundleManager.save("recipe", new BundleDefinition("dessert", "Dessert"));
        bundleManager.save("recipe", new BundleDefinition("main", "Main course"));

        fields.createStorage(FieldStorageConfig.multiple(
                "ingredients", "recipe", "string", FieldStorageConfig.UNLIMITED));
        fields.createInstance(FieldInstanceConfig.of("ingredients", "recipe", "dessert", "Dessert ingredients")
                .withDescription("What goes into the dessert")
                .asRequired());
        fields.createInstance(FieldInstanceConfig.of("ingredients", "recipe", "main", "Main ingredients")
                .withDefaultValue("salt"));
    }

    @AfterEach
    void removeFieldsAndBundles() {
        fields.deleteStorage("recipe", "ingredients");
        bundleManager.bundles("recipe").forEach(bundle -> bundleManager.delete("recipe", bundle.id()));
    }

    @Test
    void oneStorageServesEveryBundleThatUsesTheField() {
        assertThat(fields.findStorage("recipe", "ingredients")).hasValueSatisfying(storage -> {
            assertThat(storage.type()).isEqualTo("string");
            assertThat(storage.unlimited()).isTrue();
        });
    }

    @Test
    void eachBundleExposesItsOwnInstanceSettings() {
        FieldInstanceConfig dessert = fields.findInstance("recipe", "dessert", "ingredients").orElseThrow();
        FieldInstanceConfig main = fields.findInstance("recipe", "main", "ingredients").orElseThrow();

        assertThat(dessert.label()).isEqualTo("Dessert ingredients");
        assertThat(dessert.required()).isTrue();
        assertThat(dessert.description()).isEqualTo("What goes into the dessert");
        assertThat(main.label()).isEqualTo("Main ingredients");
        assertThat(main.required()).isFalse();
        assertThat(main.defaultValue()).isEqualTo("salt");
    }

    @Test
    void aBundleCarriesTheFieldsInstancedOnIt() {
        fields.createStorage(FieldStorageConfig.single("oven_temperature", "recipe", "integer"));
        fields.createInstance(FieldInstanceConfig.of("oven_temperature", "recipe", "main", "Oven temperature"));

        assertThat(fields.fieldNames("recipe", "main")).containsExactlyInAnyOrder(
                "ingredients", "oven_temperature");
        assertThat(fields.fieldNames("recipe", "dessert")).containsExactly("ingredients");

        fields.deleteStorage("recipe", "oven_temperature");
    }

    @Test
    void creatingAStorageCreatesTheFieldTables() {
        assertThat(schemaManager.tableExists(FieldTableStorage.tableName(RECIPE, "ingredients"))).isTrue();
        assertThat(schemaManager.tableExists(FieldTableStorage.revisionTableName(RECIPE, "ingredients"))).isTrue();
    }

    @Test
    void deletingAStorageDropsItsTablesAndEveryInstance() {
        fields.deleteStorage("recipe", "ingredients");

        assertThat(schemaManager.tableExists(FieldTableStorage.tableName(RECIPE, "ingredients"))).isFalse();
        assertThat(schemaManager.tableExists(FieldTableStorage.revisionTableName(RECIPE, "ingredients"))).isFalse();
        assertThat(fields.findInstance("recipe", "dessert", "ingredients")).isEmpty();
        assertThat(fields.findInstance("recipe", "main", "ingredients")).isEmpty();
        assertThat(fields.findStorage("recipe", "ingredients")).isEmpty();

        fields.createStorage(FieldStorageConfig.single("ingredients", "recipe", "string"));
    }

    @Test
    void anInstanceNeedsAStorageToAttachTo() {
        FieldInstanceConfig orphan = FieldInstanceConfig.of("nonesuch", "recipe", "main", "Nothing");

        assertThatThrownBy(() -> fields.createInstance(orphan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonesuch");
    }

    @Test
    void deletingOneInstanceLeavesTheOtherBundleUntouched() {
        fields.deleteInstance("recipe", "main", "ingredients");

        assertThat(fields.fieldNames("recipe", "main")).isEmpty();
        assertThat(fields.fieldNames("recipe", "dessert")).containsExactly("ingredients");
    }

    @Test
    void anUnlimitedFieldKeepsItsValuesInOrderAcrossBothBundles() {
        entities.save(EntityData.of("recipe", 1L, "dessert", "Tart",
                Map.of("ingredients", List.of("flour", "butter", "sugar"))));
        entities.save(EntityData.of("recipe", 2L, "main", "Stew",
                Map.of("ingredients", List.of("beef", "carrot"))));

        assertThat(entities.load("recipe", 1L)).hasValueSatisfying(loaded -> assertThat(loaded.fields())
                .containsEntry("ingredients", List.of("flour", "butter", "sugar")));
        assertThat(entities.load("recipe", 2L)).hasValueSatisfying(loaded -> assertThat(loaded.fields())
                .containsEntry("ingredients", List.of("beef", "carrot")));

        entities.delete("recipe", 1L);
        entities.delete("recipe", 2L);
    }

    @Test
    void savingFewerValuesReindexesTheDeltas() {
        entities.save(EntityData.of("recipe", 3L, "dessert", "Tart",
                Map.of("ingredients", List.of("flour", "butter", "sugar"))));

        entities.save(EntityData.of("recipe", 3L, "dessert", "Tart",
                Map.of("ingredients", List.of("sugar", "flour"))));

        assertThat(entities.load("recipe", 3L)).hasValueSatisfying(loaded -> assertThat(loaded.fields())
                .containsEntry("ingredients", List.of("sugar", "flour")));

        entities.delete("recipe", 3L);
    }

    @Test
    void aRequiredBaseFieldIsStoredWithTheEntity() {
        entityTypeManager.installStorage("portion");

        entities.save(new EntityData("portion", 1L, null, null, "One serving",
                EntityData.DEFAULT_LANGCODE, null, Map.of("amount", 250)));

        assertThat(entities.load("portion", 1L)).hasValueSatisfying(loaded ->
                assertThat(loaded.fields()).containsEntry("amount", 250));

        entities.delete("portion", 1L);
    }

    @Test
    void baseFieldsAreColumnsOfTheBaseAndRevisionTables() {
        assertThat(schemaManager.columnExists("recipe", BaseFieldDefinition.STATUS)).isTrue();
        assertThat(schemaManager.columnExists("recipe", BaseFieldDefinition.OWNER)).isTrue();
        assertThat(schemaManager.columnExists("recipe_revision", BaseFieldDefinition.CREATED)).isTrue();
        assertThat(schemaManager.columnExists("recipe_revision", BaseFieldDefinition.CHANGED)).isTrue();
    }

    @Test
    void baseFieldValuesRoundTripThroughTheBaseTable() {
        entities.save(EntityData.of("recipe", 4L, "dessert", "Tart", Map.of(
                "ingredients", List.of("flour"),
                BaseFieldDefinition.STATUS, true,
                BaseFieldDefinition.OWNER, 7L)));

        assertThat(entities.load("recipe", 4L)).hasValueSatisfying(loaded -> assertThat(loaded.fields())
                .containsEntry(BaseFieldDefinition.STATUS, true)
                .containsEntry(BaseFieldDefinition.OWNER, 7L)
                .containsEntry("ingredients", "flour"));

        entities.delete("recipe", 4L);
    }

    @Test
    void aRequiredInstanceMustBeFilledIn() {
        EntityData missingIngredients = EntityData.of("recipe", 5L, "dessert", "Empty tart", Map.of());

        assertThatThrownBy(() -> entities.save(missingIngredients))
                .isInstanceOf(EntityValidationException.class)
                .hasMessageContaining("recipe");
    }

    @Test
    void aFieldRejectsMoreValuesThanItsCardinalityAllows() {
        fields.createStorage(FieldStorageConfig.multiple("tools", "recipe", "string", 2));
        fields.createInstance(FieldInstanceConfig.of("tools", "recipe", "main", "Tools"));
        EntityData tooManyTools = EntityData.of("recipe", 6L, "main", "Stew", Map.of(
                "tools", List.of("pot", "spoon", "ladle")));

        assertThatThrownBy(() -> entities.save(tooManyTools)).isInstanceOf(EntityValidationException.class);

        fields.deleteStorage("recipe", "tools");
    }

    @Test
    void aFieldAcceptsAsManyValuesAsItsCardinalityAllows() {
        fields.createStorage(FieldStorageConfig.multiple("tools", "recipe", "string", 2));
        fields.createInstance(FieldInstanceConfig.of("tools", "recipe", "main", "Tools"));

        entities.save(EntityData.of("recipe", 7L, "main", "Stew", Map.of("tools", List.of("pot", "spoon"))));

        assertThat(entities.load("recipe", 7L)).hasValueSatisfying(loaded ->
                assertThat(loaded.fields()).containsEntry("tools", List.of("pot", "spoon")));

        entities.delete("recipe", 7L);
        fields.deleteStorage("recipe", "tools");
    }

    @TestConfiguration
    static class RecipeTypes {

        @Bean
        EntityTypeProvider recipeTypes() {
            return () -> List.of(RECIPE, RECIPE_TYPE, PORTION);
        }
    }
}
