package dev.springdrop.kernel.config.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.module.ModuleInfo;
import dev.springdrop.kernel.module.ModuleRegistry;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.user.UserEntityType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigDependencyCalculatorTest {

    private final EntityTypeManager entityTypes = mock(EntityTypeManager.class);

    private final ModuleRegistry modules = mock(ModuleRegistry.class);

    private final ConfigDependencyCalculator calculator = new ConfigDependencyCalculator(entityTypes, modules);

    @BeforeEach
    void nodesAndAccounts() {
        when(entityTypes.find(NodeEntityType.ID)).thenReturn(Optional.of(NodeEntityType.definition()));
        when(entityTypes.find(UserEntityType.ID)).thenReturn(Optional.of(UserEntityType.definition()));
        when(modules.find(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void aFieldInstanceNeedsItsStorageAndItsBundle() {
        assertThat(calculator.dependencies("field.instance.node.article.summary").config())
                .containsExactly("field.storage.node.summary", "node_type.article");
    }

    @Test
    void aFieldInstanceOnATypeWithoutBundlesNeedsOnlyItsStorage() {
        assertThat(calculator.dependencies("field.instance.user.user.nickname").config())
                .containsExactly("field.storage.user.nickname");
    }

    @Test
    void formAndViewDisplaysNeedTheirBundle() {
        assertThat(calculator.dependencies("core.form_display.node.article.default").config())
                .containsExactly("node_type.article");
        assertThat(calculator.dependencies("core.view_display.node.article.teaser").config())
                .containsExactly("node_type.article");
    }

    @Test
    void aLayoutBuilderDisplayNeedsTheViewDisplayItReplaces() {
        assertThat(calculator.dependencies("layout_builder.display.node.article.full").config())
                .containsExactly("core.view_display.node.article.full");
    }

    @Test
    void namesOfOtherShapesNeedNoOtherConfig() {
        assertThat(calculator.dependencies("field.instance.node.article").config()).isEmpty();
        assertThat(calculator.dependencies("core.form_display.node").config()).isEmpty();
        assertThat(calculator.dependencies("core.view_display.node.article").config()).isEmpty();
        assertThat(calculator.dependencies("layout_builder.display.node").config()).isEmpty();
        assertThat(calculator.dependencies("system.site")).isEqualTo(ConfigDependencies.NONE);
    }

    @Test
    void anObjectNamedForAModuleNeedsThatModule() {
        when(modules.find("example")).thenReturn(Optional.of(new ModuleInfo("example", "Example", "", "1.0",
                List.of())));

        assertThat(calculator.dependencies("example.settings").module()).containsExactly("example");
    }

    @Test
    void missingListsAreEmpty() {
        assertThat(new ConfigDependencies(null, null)).isEqualTo(ConfigDependencies.NONE);
    }
}
