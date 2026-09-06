package dev.springdrop.kernel.field;

import java.util.Map;

/**
 * How a field is stored, shared by every bundle that carries the field: the
 * field's machine name, the entity type it belongs to, its field type, how many
 * values it holds, and the settings that shape its storage. Stored as a config
 * entity named {@code field.storage.<entity type>.<field>}.
 */
public record FieldStorageConfig(
        String name,
        String entityTypeId,
        String type,
        int cardinality,
        Map<String, Object> settings) {

    /** A field that holds as many values as are given to it. */
    public static final int UNLIMITED = -1;

    public static final String CONFIG_PREFIX = "field.storage";

    public FieldStorageConfig {
        settings = Map.copyOf(settings);
    }

    public static FieldStorageConfig single(String name, String entityTypeId, String type) {
        return new FieldStorageConfig(name, entityTypeId, type, 1, Map.of());
    }

    public static FieldStorageConfig multiple(String name, String entityTypeId, String type, int cardinality) {
        return new FieldStorageConfig(name, entityTypeId, type, cardinality, Map.of());
    }

    public static String configName(String entityTypeId, String fieldName) {
        return CONFIG_PREFIX + "." + entityTypeId + "." + fieldName;
    }

    public boolean unlimited() {
        return cardinality == UNLIMITED;
    }
}
