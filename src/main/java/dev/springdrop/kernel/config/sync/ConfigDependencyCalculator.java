package dev.springdrop.kernel.config.sync;

import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.layout.LayoutBuilderDisplay;
import dev.springdrop.kernel.module.ModuleRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Works out a config object's dependencies from its name. A field instance
 * needs its field storage and its bundle, a form or view display needs its
 * bundle, and a Layout Builder display needs the view display it replaces. An
 * object whose name starts with a module's machine name needs that module.
 */
@Component
public class ConfigDependencyCalculator {

    private final EntityTypeManager entityTypes;
    private final ModuleRegistry modules;

    public ConfigDependencyCalculator(EntityTypeManager entityTypes, ModuleRegistry modules) {
        this.entityTypes = entityTypes;
        this.modules = modules;
    }

    public ConfigDependencies dependencies(String name) {
        String[] parts = name.split("\\.");
        List<String> config = new ArrayList<>();
        if (name.startsWith(FieldInstanceConfig.CONFIG_PREFIX + ".") && parts.length == 5) {
            config.add(FieldStorageConfig.configName(parts[2], parts[4]));
            bundleConfig(parts[2], parts[3]).ifPresent(config::add);
        } else if ((name.startsWith(FormDisplayConfig.CONFIG_PREFIX + ".")
                || name.startsWith(ViewDisplayConfig.CONFIG_PREFIX + ".")) && parts.length == 5) {
            bundleConfig(parts[2], parts[3]).ifPresent(config::add);
        } else if (name.startsWith(LayoutBuilderDisplay.CONFIG_PREFIX + ".") && parts.length == 5) {
            config.add(ViewDisplayConfig.configName(parts[2], parts[3], parts[4]));
        }
        List<String> module = modules.find(parts[0]).isPresent() ? List.of(parts[0]) : List.of();
        return new ConfigDependencies(config, module);
    }

    /** The config object defining a bundle, for an entity type that has bundles. */
    private Optional<String> bundleConfig(String entityTypeId, String bundle) {
        return entityTypes.find(entityTypeId).map(EntityType::bundleEntityType).map(type -> type + "." + bundle);
    }
}
