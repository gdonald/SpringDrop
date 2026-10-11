package dev.springdrop.kernel.language;

import java.util.Map;

/**
 * The translation of one config object into one language, stored as the
 * config object {@code config_translation.<langcode>.<config name>}: the
 * translated string for each translated path. The object itself is left as
 * it is.
 */
public record ConfigTranslation(Map<String, String> values) {

    public static final String CONFIG_PREFIX = "config_translation";

    public ConfigTranslation {
        values = Map.copyOf(values);
    }

    public static String configName(String langcode, String translatedName) {
        return CONFIG_PREFIX + "." + langcode + "." + translatedName;
    }
}
