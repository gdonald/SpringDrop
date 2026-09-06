package dev.springdrop.kernel.field;

import java.util.Map;

/**
 * A field as one bundle carries it: the label and description an editor sees,
 * whether it is required, its default value, and per-bundle settings. Several
 * bundles share one {@link FieldStorageConfig} while each keeps its own
 * instance. Stored as a config entity named
 * {@code field.instance.<entity type>.<bundle>.<field>}.
 */
public record FieldInstanceConfig(
        String fieldName,
        String entityTypeId,
        String bundle,
        String label,
        String description,
        boolean required,
        Object defaultValue,
        Map<String, Object> settings) {

    public static final String CONFIG_PREFIX = "field.instance";

    public FieldInstanceConfig {
        settings = Map.copyOf(settings);
    }

    public static FieldInstanceConfig of(String fieldName, String entityTypeId, String bundle, String label) {
        return new FieldInstanceConfig(fieldName, entityTypeId, bundle, label, "", false, null, Map.of());
    }

    public static String configName(String entityTypeId, String bundle, String fieldName) {
        return CONFIG_PREFIX + "." + entityTypeId + "." + bundle + "." + fieldName;
    }

    public FieldInstanceConfig asRequired() {
        return new FieldInstanceConfig(
                fieldName, entityTypeId, bundle, label, description, true, defaultValue, settings);
    }

    public FieldInstanceConfig withDescription(String newDescription) {
        return new FieldInstanceConfig(
                fieldName, entityTypeId, bundle, label, newDescription, required, defaultValue, settings);
    }

    public FieldInstanceConfig withSettings(Map<String, Object> newSettings) {
        return new FieldInstanceConfig(
                fieldName, entityTypeId, bundle, label, description, required, defaultValue, newSettings);
    }

    public FieldInstanceConfig withDefaultValue(Object value) {
        return new FieldInstanceConfig(
                fieldName, entityTypeId, bundle, label, description, required, value, settings);
    }
}
