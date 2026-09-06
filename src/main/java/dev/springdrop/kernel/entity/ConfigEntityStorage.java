package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Stores config entities in the config store, one object per entity named
 * {@code <entity type>.<id>}. There is no schema to create: a config entity
 * exists as soon as it is saved, and is exported with the rest of the site's
 * configuration.
 */
@Component
public class ConfigEntityStorage implements EntityStorage {

    private final ConfigStore configStore;

    public ConfigEntityStorage(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public static String configName(EntityType type, Object id) {
        return type.id() + "." + id;
    }

    @Override
    public void install(EntityType type) {
        // Config entities live in the config store, which the kernel baseline creates.
    }

    @Override
    public void uninstall(EntityType type) {
        // Removing a config entity type's objects is the uninstall hook's job,
        // since only the module knows which ids it created.
    }

    @Override
    public Object nextId(EntityType type) {
        throw new IllegalStateException(
                "Config entities of type '" + type.id() + "' are named by their creator, not numbered");
    }

    @Override
    public Optional<Map<String, Object>> load(EntityType type, Object id) {
        Map<?, ?> stored = configStore.read(configName(type, id), Map.class, null);
        if (stored == null) {
            return Optional.empty();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        stored.forEach((key, value) -> values.put(String.valueOf(key), value));
        return Optional.of(values);
    }

    @Override
    public void save(EntityType type, Object id, Map<String, Object> values) {
        Map<String, Object> stored = new LinkedHashMap<>(values);
        stored.put(type.keys().id(), id);
        configStore.save(configName(type, id), stored);
    }

    @Override
    public void delete(EntityType type, Object id) {
        configStore.delete(configName(type, id));
    }
}
