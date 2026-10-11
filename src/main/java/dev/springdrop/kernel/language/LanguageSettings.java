package dev.springdrop.kernel.language;

/** The language settings, stored as the config object {@code language.settings}: the site's default language. */
public record LanguageSettings(String defaultLangcode) {

    public static final String CONFIG_NAME = "language.settings";

    public static final LanguageSettings DEFAULTS = new LanguageSettings(LanguageManager.ENGLISH);
}
