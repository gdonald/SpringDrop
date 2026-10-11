package dev.springdrop.kernel.language;

/**
 * A language the site is shown in, stored as the config object
 * {@code language.entity.<langcode>}: its code, its name, which way its text
 * runs, its place among the others, and the URL prefix and domain that select
 * it. An empty prefix or domain selects nothing.
 */
public record LanguageConfig(String id, String label, String direction, int weight, String prefix, String domain) {

    public static final String CONFIG_PREFIX = "language.entity";

    public static final String LTR = "ltr";

    public static final String RTL = "rtl";

    public static String configName(String langcode) {
        return CONFIG_PREFIX + "." + langcode;
    }
}
