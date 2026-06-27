package dev.springdrop.kernel.module;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

/**
 * Installs, enables, disables, and uninstalls modules. Enabling a module first
 * enables its dependencies and runs its install hook the first time; disabling
 * or uninstalling is blocked while a dependent is still enabled or installed.
 * Enabled/installed state is recorded in the {@code module} table.
 */
@Component
public class ModuleManager {

    private static final Table<?> MODULE = table("module");
    private static final Field<String> NAME = field("name", SQLDataType.VARCHAR);
    private static final Field<Boolean> ENABLED = field("enabled", SQLDataType.BOOLEAN);

    private final ModuleRegistry registry;
    private final Map<String, ModuleLifecycle> lifecycles;
    private final DSLContext dsl;

    public ModuleManager(ModuleRegistry registry, List<ModuleLifecycle> lifecycles, DSLContext dsl) {
        this.registry = registry;
        this.lifecycles = lifecycles.stream()
                .collect(Collectors.toMap(ModuleLifecycle::moduleName, Function.identity()));
        this.dsl = dsl;
    }

    public void enable(String name) {
        ModuleInfo module = registry.find(name)
                .orElseThrow(() -> new ModuleDependencyException("Unknown module '" + name + "'"));
        for (String dependency : module.dependencies()) {
            if (!isEnabled(dependency)) {
                enable(dependency);
            }
        }
        if (isEnabled(name)) {
            return;
        }
        Optional<ModuleLifecycle> lifecycle = Optional.ofNullable(lifecycles.get(name));
        if (isInstalled(name)) {
            dsl.update(MODULE).set(ENABLED, true).where(NAME.eq(name)).execute();
        } else {
            lifecycle.ifPresent(ModuleLifecycle::onInstall);
            dsl.insertInto(MODULE).columns(NAME, ENABLED).values(name, true).execute();
        }
        lifecycle.ifPresent(ModuleLifecycle::onEnable);
    }

    public void disable(String name) {
        requireNoEnabledDependents(name);
        if (!isEnabled(name)) {
            return;
        }
        Optional.ofNullable(lifecycles.get(name)).ifPresent(ModuleLifecycle::onDisable);
        dsl.update(MODULE).set(ENABLED, false).where(NAME.eq(name)).execute();
    }

    public void uninstall(String name) {
        requireNoInstalledDependents(name);
        if (!isInstalled(name)) {
            return;
        }
        Optional.ofNullable(lifecycles.get(name)).ifPresent(ModuleLifecycle::onUninstall);
        dsl.deleteFrom(MODULE).where(NAME.eq(name)).execute();
    }

    public boolean isInstalled(String name) {
        return dsl.fetchExists(dsl.selectOne().from(MODULE).where(NAME.eq(name)));
    }

    public boolean isEnabled(String name) {
        return dsl.fetchExists(dsl.selectOne().from(MODULE).where(NAME.eq(name)).and(ENABLED.eq(true)));
    }

    private void requireNoEnabledDependents(String name) {
        for (ModuleInfo other : registry.all()) {
            if (other.dependencies().contains(name) && isEnabled(other.name())) {
                throw new ModuleDependencyException(
                        "Cannot disable '" + name + "': '" + other.name() + "' depends on it");
            }
        }
    }

    private void requireNoInstalledDependents(String name) {
        for (ModuleInfo other : registry.all()) {
            if (other.dependencies().contains(name) && isInstalled(other.name())) {
                throw new ModuleDependencyException(
                        "Cannot uninstall '" + name + "': '" + other.name() + "' depends on it");
            }
        }
    }
}
