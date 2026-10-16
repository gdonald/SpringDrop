package dev.springdrop.kernel.field;

import dev.springdrop.kernel.config.sync.ConfigSyncHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Creates a field storage's tables when an import adds it, and drops them when an import removes it. */
@Component
public class FieldStorageSyncHandler implements ConfigSyncHandler {

    private final FieldConfigManager fields;
    private final ObjectMapper objectMapper;

    public FieldStorageSyncHandler(FieldConfigManager fields, ObjectMapper objectMapper) {
        this.fields = fields;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean handles(String name) {
        return name.startsWith(FieldStorageConfig.CONFIG_PREFIX + ".");
    }

    @Override
    public void create(String name, JsonNode data) {
        fields.createStorage(objectMapper.treeToValue(data, FieldStorageConfig.class));
    }

    @Override
    public void delete(String name) {
        String[] parts = name.split("\\.");
        fields.deleteStorage(parts[2], parts[3]);
    }
}
