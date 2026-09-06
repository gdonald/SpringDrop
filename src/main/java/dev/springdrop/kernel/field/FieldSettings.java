package dev.springdrop.kernel.field;

import java.util.Map;

/**
 * Reads a setting from a field's storage or instance settings, falling back to
 * the field type's default when the site has not set one.
 */
public interface FieldSettings {

    String MAX_LENGTH = "max_length";

    String MIN = "min";

    String MAX = "max";

    String PRECISION = "precision";

    String SCALE = "scale";

    String ON_LABEL = "on_label";

    String OFF_LABEL = "off_label";

    String ALLOWED_VALUES = "allowed_values";

    static Object read(Map<String, Object> settings, Map<String, Object> defaults, String key) {
        return settings.containsKey(key) ? settings.get(key) : defaults.get(key);
    }

    static Number number(Map<String, Object> settings, Map<String, Object> defaults, String key) {
        return (Number) read(settings, defaults, key);
    }
}
