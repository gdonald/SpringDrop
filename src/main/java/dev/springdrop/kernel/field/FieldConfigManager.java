package dev.springdrop.kernel.field;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BundleFieldMap;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.FieldTableStorage;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Creates and removes field storage and field instances, and the tables behind
 * them. Creating a storage builds the field's tables; deleting it drops them and
 * every instance that used it. An instance attaches an existing storage to one
 * bundle with its own label, description, and default value.
 */
@Component
public class FieldConfigManager implements BundleFieldMap {

    private final ConfigStore configStore;
    private final EntityTypeManager entityTypeManager;
    private final FieldTableStorage fieldTableStorage;
    private final PluginRegistry pluginRegistry;

    public FieldConfigManager(
            ConfigStore configStore,
            EntityTypeManager entityTypeManager,
            FieldTableStorage fieldTableStorage,
            PluginRegistry pluginRegistry) {
        this.configStore = configStore;
        this.entityTypeManager = entityTypeManager;
        this.fieldTableStorage = fieldTableStorage;
        this.pluginRegistry = pluginRegistry;
    }

    /** The field type plugin a storage is built on. */
    public FieldType fieldType(String fieldTypeId) {
        return pluginRegistry.managerFor(FieldType.class).get(fieldTypeId);
    }

    public void createStorage(FieldStorageConfig storage) {
        fieldType(storage.type());
        configStore.save(
                FieldStorageConfig.configName(storage.entityTypeId(), storage.name()), storage);
        fieldTableStorage.install(entityType(storage.entityTypeId()), storage.name());
    }

    /** Removes the field's tables, its storage, and every instance of it. */
    public void deleteStorage(String entityTypeId, String fieldName) {
        for (FieldInstanceConfig instance : instancesOfField(entityTypeId, fieldName)) {
            deleteInstance(entityTypeId, instance.bundle(), fieldName);
        }
        fieldTableStorage.uninstall(entityType(entityTypeId), fieldName);
        configStore.delete(FieldStorageConfig.configName(entityTypeId, fieldName));
    }

    public void createInstance(FieldInstanceConfig instance) {
        requireStorage(instance.entityTypeId(), instance.fieldName());
        configStore.save(
                FieldInstanceConfig.configName(
                        instance.entityTypeId(), instance.bundle(), instance.fieldName()),
                instance);
    }

    public void deleteInstance(String entityTypeId, String bundle, String fieldName) {
        configStore.delete(FieldInstanceConfig.configName(entityTypeId, bundle, fieldName));
    }

    public Optional<FieldStorageConfig> findStorage(String entityTypeId, String fieldName) {
        return Optional.ofNullable(configStore.read(
                FieldStorageConfig.configName(entityTypeId, fieldName), FieldStorageConfig.class, null));
    }

    public Optional<FieldInstanceConfig> findInstance(String entityTypeId, String bundle, String fieldName) {
        return Optional.ofNullable(configStore.read(
                FieldInstanceConfig.configName(entityTypeId, bundle, fieldName), FieldInstanceConfig.class, null));
    }

    /** Every field instance attached to one bundle, in the order they were created. */
    public List<FieldInstanceConfig> instances(String entityTypeId, String bundle) {
        List<FieldInstanceConfig> instances = new ArrayList<>();
        String prefix = FieldInstanceConfig.CONFIG_PREFIX + "." + entityTypeId + "." + bundle;
        for (String name : configStore.listNames(prefix)) {
            String fieldName = name.substring(prefix.length() + 1);
            findInstance(entityTypeId, bundle, fieldName).ifPresent(instances::add);
        }
        return List.copyOf(instances);
    }

    @Override
    public List<String> fieldNames(String entityTypeId, String bundle) {
        return instances(entityTypeId, bundle).stream().map(instance -> instance.fieldName()).toList();
    }

    private List<FieldInstanceConfig> instancesOfField(String entityTypeId, String fieldName) {
        List<FieldInstanceConfig> instances = new ArrayList<>();
        String prefix = FieldInstanceConfig.CONFIG_PREFIX + "." + entityTypeId;
        for (String name : configStore.listNames(prefix)) {
            if (name.endsWith("." + fieldName)) {
                String bundle = name.substring(prefix.length() + 1, name.length() - fieldName.length() - 1);
                findInstance(entityTypeId, bundle, fieldName).ifPresent(instances::add);
            }
        }
        return instances;
    }

    private void requireStorage(String entityTypeId, String fieldName) {
        findStorage(entityTypeId, fieldName).orElseThrow(() -> new IllegalArgumentException(
                "No field storage '" + fieldName + "' on entity type '" + entityTypeId + "'"));
    }

    private EntityType entityType(String entityTypeId) {
        return entityTypeManager.require(entityTypeId);
    }
}
