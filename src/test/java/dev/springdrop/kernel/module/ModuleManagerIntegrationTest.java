package dev.springdrop.kernel.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.jooq.impl.DSL.table;

import dev.springdrop.kernel.schema.ColumnSpec;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class ModuleManagerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ModuleManager moduleManager;

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    @AfterEach
    void clean() {
        dsl.deleteFrom(table("module")).execute();
        schemaManager.dropTable("widgets_data");
    }

    @Test
    void enablingInstallsTheModuleItsSchemaAndItsDependencies() {
        moduleManager.enable("widgets");

        assertThat(moduleManager.isEnabled("widgets")).isTrue();
        assertThat(moduleManager.isEnabled("foundation")).isTrue();
        assertThat(schemaManager.tableExists("widgets_data")).isTrue();
    }

    @Test
    void enablingIsIdempotent() {
        moduleManager.enable("widgets");
        moduleManager.enable("widgets");

        assertThat(moduleManager.isEnabled("widgets")).isTrue();
    }

    @Test
    void enablingSkipsDependenciesThatAreAlreadyEnabled() {
        moduleManager.enable("content");

        moduleManager.enable("blog");

        assertThat(moduleManager.isEnabled("blog")).isTrue();
    }

    @Test
    void reEnablingAnInstalledModuleDoesNotReinstall() {
        moduleManager.enable("widgets");
        moduleManager.disable("widgets");

        moduleManager.enable("widgets");

        assertThat(moduleManager.isEnabled("widgets")).isTrue();
        assertThat(moduleManager.isInstalled("widgets")).isTrue();
    }

    @Test
    void disablingClearsTheEnabledFlagButKeepsItInstalled() {
        moduleManager.enable("widgets");

        moduleManager.disable("widgets");

        assertThat(moduleManager.isEnabled("widgets")).isFalse();
        assertThat(moduleManager.isInstalled("widgets")).isTrue();
    }

    @Test
    void disablingAModuleThatIsNotEnabledIsANoOp() {
        moduleManager.disable("widgets");

        assertThat(moduleManager.isInstalled("widgets")).isFalse();
    }

    @Test
    void disablingIsBlockedByAnEnabledDependent() {
        moduleManager.enable("content");

        assertThatThrownBy(() -> moduleManager.disable("foundation"))
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("depends on it");
    }

    @Test
    void disablingIsAllowedOnceTheDependentIsDisabled() {
        moduleManager.enable("content");
        moduleManager.disable("content");

        moduleManager.disable("foundation");

        assertThat(moduleManager.isEnabled("foundation")).isFalse();
    }

    @Test
    void uninstallingRunsTheUninstallHookAndRemovesState() {
        moduleManager.enable("widgets");
        moduleManager.disable("widgets");

        moduleManager.uninstall("widgets");

        assertThat(moduleManager.isInstalled("widgets")).isFalse();
        assertThat(schemaManager.tableExists("widgets_data")).isFalse();
    }

    @Test
    void uninstallingAModuleWithoutALifecycleRemovesItsState() {
        moduleManager.enable("foundation");
        moduleManager.disable("foundation");

        moduleManager.uninstall("foundation");

        assertThat(moduleManager.isInstalled("foundation")).isFalse();
    }

    @Test
    void uninstallingIsBlockedByAnInstalledDependent() {
        moduleManager.enable("content");

        assertThatThrownBy(() -> moduleManager.uninstall("foundation"))
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("depends on it");
    }

    @Test
    void uninstallingAModuleThatIsNotInstalledIsANoOp() {
        moduleManager.uninstall("widgets");

        assertThat(moduleManager.isInstalled("widgets")).isFalse();
    }

    @Test
    void enablingAnUnknownModuleIsRejected() {
        assertThatThrownBy(() -> moduleManager.enable("nonexistent"))
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("Unknown module");
    }

    @TestConfiguration
    static class WidgetsModule {

        @Bean
        ModuleLifecycle widgetsLifecycle(SchemaManager schemaManager) {
            return new ModuleLifecycle() {
                @Override
                public String moduleName() {
                    return "widgets";
                }

                @Override
                public void onInstall() {
                    schemaManager.createTable(
                            "widgets_data", List.of(ColumnSpec.required("id", ColumnType.BIGINT)), "id");
                }

                @Override
                public void onEnable() {
                }

                @Override
                public void onDisable() {
                }

                @Override
                public void onUninstall() {
                    schemaManager.dropTable("widgets_data");
                }
            };
        }
    }
}
