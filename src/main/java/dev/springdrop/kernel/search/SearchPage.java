package dev.springdrop.kernel.search;

/**
 * A search page, stored as the config object {@code search.page.<id>}: its
 * label, the path under {@code /search} it answers at, the view drawing its
 * form and results, and its place among the search pages.
 */
public record SearchPage(String id, String label, String path, String viewId, int weight) {

    public static final String CONFIG_PREFIX = "search.page";

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public String address() {
        return SearchPageManager.PATH + "/" + path;
    }
}
