package dev.springdrop.kernel.config.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.batch.BatchDefinition;
import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchOperationSpec;
import dev.springdrop.kernel.batch.BatchProgress;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ConfigSyncIntegrationTest extends AbstractIntegrationTest {

    private static final String RECIPE = "recipe";

    private static final String INGREDIENTS = "ingredients";

    private static final String BAKER = "baker";

    private static final String STORAGE = FieldStorageConfig.configName(NodeEntityType.ID, INGREDIENTS);

    private static final String INSTANCE = FieldInstanceConfig.configName(NodeEntityType.ID, RECIPE, INGREDIENTS);

    @TempDir
    Path directory;

    @Autowired
    private ConfigSync sync;

    @Autowired
    private ConfigImportBatch imports;

    @Autowired
    private BatchManager batches;

    @Autowired
    private ConfigStore config;

    @Autowired
    private ConfigFiles files;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private RoleManager roles;

    @Autowired
    private NodeService nodes;

    @Autowired
    private ModuleManager modules;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void aRecipeTypeWithAFieldAndABakerRole() {
        clear();
        types.save(NodeType.of(RECIPE, "Recipe"));
        fields.createStorage(FieldStorageConfig.single(INGREDIENTS, NodeEntityType.ID, StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(INGREDIENTS, NodeEntityType.ID, RECIPE, "Ingredients"));
        roles.save(RoleConfig.of(BAKER, "Baker", 4));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        fields.findStorage(NodeEntityType.ID, INGREDIENTS)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, INGREDIENTS));
        types.find(RECIPE).ifPresent(type -> types.delete(RECIPE));
        List.of(BAKER, "bakers").forEach(role -> roles.find(role).ifPresent(found -> roles.delete(found.id())));
        config.delete("example.leftover");
    }

    private BatchProgress importThroughABatch() {
        return batches.processAll(imports.start(directory, "/").batchId());
    }

    @Test
    void exportingThenImportingOnACleanSiteReproducesIdenticalConfigWithAnEmptyDiff() {
        sync.export(directory);
        Map<String, ConfigStore.StoredConfig> exported = config.everything();
        fields.deleteStorage(NodeEntityType.ID, INGREDIENTS);
        types.delete(RECIPE);
        roles.delete(BAKER);
        assertThat(sync.changes(directory)).extracting(ConfigChange::operation)
                .containsOnly(ConfigChange.Operation.CREATE);

        BatchProgress progress = importThroughABatch();

        assertThat(progress.errors()).isEmpty();
        assertThat(config.everything()).isEqualTo(exported);
        assertThat(sync.changes(directory)).isEmpty();
        long recipe = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, RECIPE, "Bread",
                Map.of(INGREDIENTS, "Flour, water, salt")), 1L).id()).longValue();
        assertThat(nodes.find(recipe).orElseThrow().fields()).containsEntry(INGREDIENTS, "Flour, water, salt");
        nodes.delete(recipe);
    }

    @Test
    void anExportWritesEachObjectWithItsUuidAndDependencies() throws Exception {
        sync.export(directory);

        String instance = Files.readString(directory.resolve(INSTANCE + ".yml"));
        assertThat(instance).contains("uuid: \"" + config.everything().get(INSTANCE).uuid() + "\"");
        assertThat(instance).contains(STORAGE, "node_type.recipe", "label: \"Ingredients\"");
    }

    @Test
    void anExportRemovesTheFilesOfObjectsThatAreGone() {
        sync.export(directory);
        roles.delete(BAKER);

        sync.export(directory);

        assertThat(directory.resolve(RoleConfig.configName(BAKER) + ".yml")).doesNotExist();
    }

    @Nested
    class Changes {

        @Test
        void aChangedValueIsUpdated() {
            sync.export(directory);
            roles.save(RoleConfig.of(BAKER, "Head baker", 4));

            assertThat(sync.changes(directory)).containsExactly(
                    ConfigChange.of(ConfigChange.Operation.UPDATE, RoleConfig.configName(BAKER)));
            sync.apply(directory);
            assertThat(roles.find(BAKER).orElseThrow().label()).isEqualTo("Baker");
        }

        @Test
        void anObjectWithAnotherUuidIsUpdatedToTheExportedOne() {
            sync.export(directory);
            UUID exported = config.everything().get(RoleConfig.configName(BAKER)).uuid();
            roles.delete(BAKER);
            roles.save(RoleConfig.of(BAKER, "Baker", 4));

            assertThat(sync.changes(directory)).extracting(ConfigChange::operation)
                    .containsExactly(ConfigChange.Operation.UPDATE);
            sync.apply(directory);
            assertThat(config.everything().get(RoleConfig.configName(BAKER)).uuid()).isEqualTo(exported);
        }

        @Test
        void anObjectUnderANewNameWithTheSameUuidIsRenamed() {
            sync.export(directory);
            UUID uuid = config.everything().get(RoleConfig.configName(BAKER)).uuid();
            config.rename(RoleConfig.configName(BAKER), RoleConfig.configName("bakers"));

            assertThat(sync.changes(directory)).containsExactly(new ConfigChange(ConfigChange.Operation.RENAME,
                    RoleConfig.configName(BAKER), RoleConfig.configName("bakers")));
            sync.apply(directory);
            assertThat(config.everything().get(RoleConfig.configName(BAKER)).uuid()).isEqualTo(uuid);
            assertThat(config.everything()).doesNotContainKey(RoleConfig.configName("bakers"));
        }

        @Test
        void aCopyKeepingItsOriginalsUuidIsACreationThatTheUuidIndexRefuses() throws Exception {
            sync.export(directory);
            Files.copy(directory.resolve(RoleConfig.configName(BAKER) + ".yml"),
                    directory.resolve(RoleConfig.configName("bakers") + ".yml"));

            assertThat(sync.changes(directory)).containsExactly(
                    ConfigChange.of(ConfigChange.Operation.CREATE, RoleConfig.configName("bakers")));
            assertThatThrownBy(() -> sync.apply(directory)).isInstanceOf(RuntimeException.class);
            assertThat(roles.find("bakers")).isEmpty();
        }

        @Test
        void objectsAddedSinceTheExportAreDeletedAndFieldStoragesTakeTheirTablesWithThem() {
            fields.deleteStorage(NodeEntityType.ID, INGREDIENTS);
            sync.export(directory);
            fields.createStorage(FieldStorageConfig.single(INGREDIENTS, NodeEntityType.ID, StringFieldType.ID));
            fields.createInstance(FieldInstanceConfig.of(INGREDIENTS, NodeEntityType.ID, RECIPE, "Ingredients"));
            config.save("example.leftover", Map.of("kept", false));

            assertThat(sync.changes(directory)).containsExactlyInAnyOrder(
                    ConfigChange.of(ConfigChange.Operation.DELETE, "example.leftover"),
                    ConfigChange.of(ConfigChange.Operation.DELETE, INSTANCE),
                    ConfigChange.of(ConfigChange.Operation.DELETE, STORAGE));
            assertThat(sync.changes(directory)).extracting(ConfigChange::name).containsSubsequence(INSTANCE, STORAGE);
            sync.apply(directory);

            assertThat(fields.findStorage(NodeEntityType.ID, INGREDIENTS)).isEmpty();
            assertThat(config.everything()).doesNotContainKeys("example.leftover", INSTANCE);
            fields.createStorage(FieldStorageConfig.single(INGREDIENTS, NodeEntityType.ID, StringFieldType.ID));
        }

        @Test
        void creationsComeAfterTheObjectsTheyDependOn() {
            sync.export(directory);
            fields.deleteStorage(NodeEntityType.ID, INGREDIENTS);
            types.delete(RECIPE);

            assertThat(sync.changes(directory)).extracting(ConfigChange::name)
                    .containsSubsequence(STORAGE, INSTANCE).containsSubsequence("node_type.recipe", INSTANCE);
        }
    }

    @Nested
    class Validation {

        private void declare(String name, String dependencies) throws Exception {
            Path file = directory.resolve(name + ".yml");
            Files.writeString(file, Files.readString(file).replaceFirst("(?s)dependencies:.*?(?=data:)",
                    "dependencies:\n" + dependencies + "\n"));
        }

        @Test
        void anImportMissingADependencyIsRejectedAndLeavesExistingConfigUnchanged() throws Exception {
            sync.export(directory);
            Files.delete(directory.resolve("node_type.recipe.yml"));
            declare(INSTANCE, "  config: []");
            roles.save(RoleConfig.of(BAKER, "Head baker", 4));
            Map<String, ConfigStore.StoredConfig> before = config.everything();

            assertThat(sync.problems(directory)).containsExactly(
                    INSTANCE + " depends on node_type.recipe, which the import does not hold.");
            BatchProgress progress = importThroughABatch();

            assertThat(progress.errors()).anySatisfy(error -> assertThat(error).contains(
                    INSTANCE + " depends on node_type.recipe, which the import does not hold."));
            assertThat(config.everything()).isEqualTo(before);
            assertThatThrownBy(() -> sync.apply(directory)).isInstanceOfSatisfying(ConfigImportException.class,
                    refused -> assertThat(refused.problems()).hasSize(1));
        }

        @Test
        void aDeclaredDependencyOrModuleMustBeThere() throws Exception {
            sync.export(directory);
            declare(RoleConfig.configName(BAKER), "  config:\n  - \"system.nothing\"\n  module:\n  - \"pastry\"");

            assertThat(sync.problems(directory)).containsExactly(
                    RoleConfig.configName(BAKER) + " depends on system.nothing, which the import does not hold.",
                    RoleConfig.configName(BAKER) + " depends on the module pastry, which is not enabled.");
        }

        @Test
        void twoFilesWithTheSameUuidConflict() throws Exception {
            sync.export(directory);
            Files.copy(directory.resolve(RoleConfig.configName(BAKER) + ".yml"),
                    directory.resolve(RoleConfig.configName("bakers") + ".yml"));

            assertThat(sync.problems(directory)).containsExactly(RoleConfig.configName(BAKER) + " and "
                    + RoleConfig.configName("bakers") + " carry the same uuid.");
        }

        @Test
        void objectsThatDependOnEachOtherAreReportedOnce() throws Exception {
            sync.export(directory);
            Files.copy(directory.resolve(RoleConfig.configName(BAKER) + ".yml"),
                    directory.resolve(RoleConfig.configName("bakers") + ".yml"));
            Path copy = directory.resolve(RoleConfig.configName("bakers") + ".yml");
            Files.writeString(copy, Files.readString(copy).replaceFirst("uuid: \"[^\"]+\"",
                    "uuid: \"" + UUID.randomUUID() + "\""));
            declare(RoleConfig.configName(BAKER), "  config:\n  - \"user.role.bakers\"");
            declare(RoleConfig.configName("bakers"), "  config:\n  - \"user.role.baker\"");

            assertThat(sync.problems(directory)).containsExactly("The objects user.role.baker, user.role.bakers "
                    + "depend on each other.");
        }

        @Test
        void anObjectNamedForAnEnabledModuleMayBeImported() {
            modules.enable("foundation");
            config.save("foundation.settings", Map.of("depth", 2));
            try {
                sync.export(directory);

                assertThat(files.read(directory).get("foundation.settings").dependencies().module())
                        .containsExactly("foundation");
                assertThat(sync.problems(directory)).isEmpty();
            } finally {
                config.delete("foundation.settings");
                dsl.deleteFrom(DSL.table("module")).where(DSL.field("name").eq("foundation")).execute();
            }
        }

        @Test
        void aSiteMatchingItsExportHasNoProblems() {
            sync.export(directory);

            assertThat(sync.problems(directory)).isEmpty();
        }
    }

    @Nested
    class SingleItems {

        private static final String CHEF = RoleConfig.configName("chef");

        @AfterEach
        void noChef() {
            roles.find("chef").ifPresent(role -> roles.delete(role.id()));
        }

        @Test
        void anObjectIsExportedAsItsFile() {
            assertThat(sync.exportSingle(RoleConfig.configName(BAKER)).orElseThrow())
                    .contains("uuid: \"" + config.everything().get(RoleConfig.configName(BAKER)).uuid() + "\"",
                            "label: \"Baker\"");
            assertThat(sync.exportSingle("user.role.nobody")).isEmpty();
        }

        @Test
        void anObjectIsCreatedWithANewUuidOrUpdatedKeepingItsOwn() {
            UUID baker = config.everything().get(RoleConfig.configName(BAKER)).uuid();

            assertThat(sync.importSingle(CHEF, "id: chef\nlabel: Chef\nweight: 3\npermissions: []\n"
                    + "administrator: false\n")).isEmpty();
            assertThat(sync.importSingle(RoleConfig.configName(BAKER), "id: baker\nlabel: Master baker\nweight: 4\n"
                    + "permissions: []\nadministrator: false\n")).isEmpty();

            assertThat(roles.find("chef").orElseThrow().label()).isEqualTo("Chef");
            assertThat(roles.find(BAKER).orElseThrow().label()).isEqualTo("Master baker");
            assertThat(config.everything().get(RoleConfig.configName(BAKER)).uuid()).isEqualTo(baker);
        }

        @Test
        void aNewFieldStorageGetsItsTables() {
            fields.deleteStorage(NodeEntityType.ID, INGREDIENTS);

            assertThat(sync.importSingle(STORAGE, "name: ingredients\nentityTypeId: node\ntype: string\n"
                    + "cardinality: 1\nsettings: {}\n")).isEmpty();
            fields.createInstance(FieldInstanceConfig.of(INGREDIENTS, NodeEntityType.ID, RECIPE, "Ingredients"));

            long recipe = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, RECIPE, "Bread",
                    Map.of(INGREDIENTS, "Flour")), 1L).id()).longValue();
            assertThat(nodes.find(recipe).orElseThrow().fields()).containsEntry(INGREDIENTS, "Flour");
            nodes.delete(recipe);
        }

        @Test
        void aSchemaInvalidValueIsRefusedWithAClearError() {
            assertThat(sync.importSingle(CHEF, "id: chef\nlabel: Chef\nweight: heavy\npermissions: []\n"
                    + "administrator: false\n"))
                    .containsExactly(CHEF + ": weight holds heavy, which is not a whole number.");
            assertThat(roles.find("chef")).isEmpty();
        }

        @Test
        void aMissingDependencyOrModuleIsRefused() {
            assertThat(sync.importSingle("field.instance.node.recipe.steps", "fieldName: steps\n"))
                    .contains("field.instance.node.recipe.steps depends on field.storage.node.steps, which the site "
                            + "does not have.");
            assertThat(sync.importSingle("foundation.settings", "depth: 2\n")).containsExactly(
                    "foundation.settings depends on the module foundation, which is not enabled.");
        }

        @Test
        void anObjectOfAnEnabledModuleIsImported() {
            modules.enable("foundation");
            try {
                assertThat(sync.importSingle("foundation.settings", "depth: 2\n")).isEmpty();
                assertThat(config.everything().get("foundation.settings").data().get("depth").asInt()).isEqualTo(2);
            } finally {
                config.delete("foundation.settings");
                dsl.deleteFrom(DSL.table("module")).where(DSL.field("name").eq("foundation")).execute();
            }
        }

        @Test
        void yamlThatIsBlankOrCannotBeReadIsRefused() {
            assertThat(sync.importSingle(CHEF, "label: [unclosed")).containsExactly("The YAML cannot be read.");
            assertThat(sync.importSingle(CHEF, "")).containsExactly("The YAML holds no value.");
        }

        @Test
        void aFullImportRefusesAFileThatBreaksItsSchema() throws Exception {
            sync.export(directory);
            Path file = directory.resolve(RoleConfig.configName(BAKER) + ".yml");
            Files.writeString(file, Files.readString(file).replaceFirst("weight: 4", "weight: \"heavy\""));

            assertThat(sync.problems(directory)).containsExactly(RoleConfig.configName(BAKER)
                    + ": weight holds heavy, which is not a whole number.");
        }
    }

    @Nested
    class Refusals {

        @Test
        void aFileThatCannotBeReadFailsTheImportAndLeavesTheConfigAsItWas() throws Exception {
            sync.export(directory);
            roles.save(RoleConfig.of(BAKER, "Head baker", 4));
            Files.writeString(directory.resolve("broken.yml"), "data: [unclosed");

            assertThatThrownBy(() -> sync.changes(directory)).isInstanceOf(ConfigFileException.class)
                    .hasMessage("broken.yml is not YAML.");
            Files.writeString(directory.resolve(RoleConfig.configName(BAKER) + ".yml"), "data: [unclosed");
            BatchProgress progress = batches.processAll(batches.start(BatchDefinition.of(
                    "config-import-broken", List.of(
                            BatchOperationSpec.of(ConfigImportBatch.CHECK, Map.of(
                                    ConfigImportBatch.DIRECTORY, directory.toString(), ConfigImportBatch.NAME,
                                    RoleConfig.configName(BAKER))),
                            BatchOperationSpec.of(ConfigImportBatch.CHECK, Map.of(
                                    ConfigImportBatch.DIRECTORY, directory.toString(), ConfigImportBatch.NAME,
                                    "deleted.object")),
                            BatchOperationSpec.of(ConfigImportBatch.APPLY, Map.of(
                                    ConfigImportBatch.DIRECTORY, directory.toString()))))).batchId());

            assertThat(progress.errors()).hasSize(2);
            assertThat(roles.find(BAKER).orElseThrow().label()).isEqualTo("Head baker");
        }

        @Test
        void aFileWithoutAUuidOrDataIsRefused() throws Exception {
            Files.writeString(directory.resolve("no.uuid.yml"), "data:\n  label: x\n");
            assertThatThrownBy(() -> files.read(directory)).hasMessage("no.uuid.yml has no uuid.");
            Files.delete(directory.resolve("no.uuid.yml"));
            Files.writeString(directory.resolve("no.data.yml"), "uuid: \"" + UUID.randomUUID() + "\"\n");
            assertThatThrownBy(() -> files.read(directory)).hasMessage("no.data.yml has no data.");
            Files.writeString(directory.resolve("no.data.yml"), "- a list\n");
            assertThatThrownBy(() -> files.read(directory)).hasMessage("no.data.yml has no data.");
        }

        @Test
        void aFileWithoutDependenciesDependsOnNothing() throws Exception {
            Files.writeString(directory.resolve("plain.settings.yml"), "uuid: \"" + UUID.randomUUID()
                    + "\"\ndata:\n  enabled: true\n");

            assertThat(files.read(directory).get("plain.settings").dependencies()).isEqualTo(ConfigDependencies.NONE);
        }
    }

    @Nested
    class FileHandling {

        @Test
        void aDirectoryThatIsNotThereHoldsNoFiles() {
            assertThat(files.read(directory.resolve("missing"))).isEmpty();
        }

        @Test
        void clearingLeavesFilesThatAreNotConfigAlone() throws Exception {
            Files.writeString(directory.resolve("README.txt"), "Exported configuration.");
            sync.export(directory);

            files.clear(directory);

            assertThat(directory.resolve("README.txt")).exists();
            assertThat(files.read(directory)).isEmpty();
        }

        @Test
        void aDirectoryThatCannotBeWrittenIsReported() throws Exception {
            Path blocked = Files.writeString(directory.resolve("blocked"), "a file, not a directory");

            assertThatThrownBy(() -> sync.export(blocked.resolve("sync"))).isInstanceOf(UncheckedIOException.class);
        }

        @Test
        void aListingThatCannotBeReadIsReported() throws Exception {
            Path locked = Files.createDirectory(directory.resolve("locked"));
            assertThat(locked.toFile().setReadable(false)).isTrue();
            try {
                assertThatThrownBy(() -> files.read(locked)).isInstanceOf(UncheckedIOException.class);
            } finally {
                locked.toFile().setReadable(true);
            }
        }
    }
}
