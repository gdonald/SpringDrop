package dev.springdrop.kernel.field.display;

import java.util.Map;

/**
 * Where one field sits in a display: which widget or formatter handles it, how
 * far up the order it comes, and the settings that handler runs with.
 */
public record FieldDisplaySlot(String fieldName, String handler, int weight, Map<String, Object> settings) {

    public FieldDisplaySlot {
        settings = Map.copyOf(settings);
    }

    public static FieldDisplaySlot of(String fieldName, String handler, int weight) {
        return new FieldDisplaySlot(fieldName, handler, weight, Map.of());
    }
}
