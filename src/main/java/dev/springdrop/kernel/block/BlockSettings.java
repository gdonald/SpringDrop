package dev.springdrop.kernel.block;

import java.util.List;
import java.util.Map;

/**
 * Reads settings back out of a stored placement. Settings come back from the
 * config store as plain JSON values, so each is read as the type it was written
 * as, and a setting that is missing reads as empty.
 */
public interface BlockSettings {

    static String string(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        return (value == null) ? "" : value.toString();
    }

    static List<String> strings(Map<String, Object> settings, String key) {
        return (settings.get(key) instanceof List<?> values)
                ? values.stream().map(String::valueOf).toList()
                : List.of();
    }
}
