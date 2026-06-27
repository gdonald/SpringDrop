package dev.springdrop.kernel.module;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ModuleDependencyResolverTest {

    private final ModuleDependencyResolver resolver = new ModuleDependencyResolver();

    private ModuleInfo module(String name, String... dependencies) {
        return new ModuleInfo(name, name, name, "1.0", List.of(dependencies));
    }

    @Test
    void ordersDependenciesBeforeDependents() {
        List<ModuleInfo> order = resolver.resolveInstallOrder(List.of(
                module("blog", "content"),
                module("content", "foundation"),
                module("foundation")));

        assertThat(order).extracting(ModuleInfo::name).containsExactly("foundation", "content", "blog");
    }

    @Test
    void visitsASharedDependencyOnlyOnce() {
        List<ModuleInfo> order = resolver.resolveInstallOrder(List.of(
                module("left", "base"),
                module("right", "base"),
                module("base"),
                module("top", "left", "right")));

        assertThat(order).extracting(ModuleInfo::name).hasSize(4);
        assertThat(order.indexOf(byName(order, "base"))).isLessThan(order.indexOf(byName(order, "top")));
    }

    @Test
    void rejectsAMissingDependency() {
        assertThatThrownBy(() -> resolver.resolveInstallOrder(List.of(module("content", "foundation"))))
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("missing module 'foundation'");
    }

    @Test
    void rejectsACircularDependency() {
        assertThatThrownBy(() -> resolver.resolveInstallOrder(List.of(
                module("a", "b"),
                module("b", "a"))))
                .isInstanceOf(ModuleDependencyException.class)
                .hasMessageContaining("Circular dependency");
    }

    private ModuleInfo byName(List<ModuleInfo> modules, String name) {
        return modules.stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }
}
