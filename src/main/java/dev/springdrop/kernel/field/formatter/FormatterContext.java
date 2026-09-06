package dev.springdrop.kernel.field.formatter;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import java.util.Map;

/**
 * What a formatter needs to know about the field it is rendering: how it is
 * stored, how the bundle presents it, and the settings the display gives the
 * formatter itself.
 */
public record FormatterContext(
        FieldStorageConfig storage, FieldInstanceConfig instance, Map<String, Object> settings) {

    public FormatterContext {
        settings = Map.copyOf(settings);
    }

    public String fieldName() {
        return storage.name();
    }

    public String label() {
        return instance.label();
    }

    public Object setting(String key, Object fallback) {
        return settings.getOrDefault(key, fallback);
    }

    public String text(String key, String fallback) {
        return String.valueOf(setting(key, fallback));
    }
}
