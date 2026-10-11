package dev.springdrop.kernel.language;

import java.util.List;

/**
 * The config objects of one kind that can be translated, such as field
 * instances, and which of their strings. Each object of the kind is named
 * {@code <prefix>.<rest>}, or for a kind with one object, such as the site
 * information, the prefix itself. A key is a path into the object: {@code label}, or
 * for a list of items carrying ids, {@code displays.*.title}, the title of
 * each item, told apart by its id.
 */
public record ConfigTranslationMapper(String prefix, String typeLabel, List<TranslatableKey> keys) {

    public ConfigTranslationMapper {
        keys = List.copyOf(keys);
    }

    /** One translatable string's path and what the translation form calls it. */
    public record TranslatableKey(String path, String label) {
    }

    public boolean covers(String configName) {
        return configName.equals(prefix) || configName.startsWith(prefix + ".");
    }
}
