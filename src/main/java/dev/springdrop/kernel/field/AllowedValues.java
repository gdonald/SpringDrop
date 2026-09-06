package dev.springdrop.kernel.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The options of a list field, kept in the storage's {@code allowed_values}
 * setting as a list of value and label pairs. A list keeps each value's own
 * type, which a map keyed by value could not: stored settings are JSON, where
 * every key is text.
 */
public interface AllowedValues {

    String VALUE_KEY = "value";

    String LABEL_KEY = "label";

    /** The settings value holding these options. */
    static Object setting(List<AllowedValue> options) {
        return options.stream()
                .map(option -> Map.of(VALUE_KEY, option.value(), LABEL_KEY, option.label()))
                .toList();
    }

    /** The options a field of this storage offers, in the order they are offered. */
    static List<AllowedValue> labelled(FieldStorageConfig storage) {
        List<AllowedValue> options = new ArrayList<>();
        for (Map<?, ?> option : stored(storage)) {
            options.add(new AllowedValue(option.get(VALUE_KEY), String.valueOf(option.get(LABEL_KEY))));
        }
        return List.copyOf(options);
    }

    /** The values a field of this storage may hold, in the order they are offered. */
    static List<Object> of(FieldStorageConfig storage) {
        List<Object> values = new ArrayList<>();
        for (Map<?, ?> option : stored(storage)) {
            values.add(option.get(VALUE_KEY));
        }
        return List.copyOf(values);
    }

    private static List<Map<?, ?>> stored(FieldStorageConfig storage) {
        Object setting = storage.settings().get(FieldSettings.ALLOWED_VALUES);
        if (setting == null) {
            return List.of();
        }
        List<Map<?, ?>> options = new ArrayList<>();
        for (Object option : (List<?>) setting) {
            options.add((Map<?, ?>) option);
        }
        return options;
    }
}
