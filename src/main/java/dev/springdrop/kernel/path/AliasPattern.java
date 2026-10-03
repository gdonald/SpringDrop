package dev.springdrop.kernel.path;

/**
 * How the aliases of one bundle's pages are made: a pattern of text and tokens,
 * such as {@code /blog/[node:title]}, and whether an alias made from it is made
 * again each time the page is saved, so it follows a changed title. Stored as
 * the config object {@code path.pattern.<entity type>.<bundle>}.
 */
public record AliasPattern(String entityType, String bundle, String pattern, boolean regenerate) {

    public static final String CONFIG_PREFIX = "path.pattern";

    public static String configName(String entityType, String bundle) {
        return CONFIG_PREFIX + "." + entityType + "." + bundle;
    }
}
