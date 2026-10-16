package dev.springdrop.kernel.cache;

/**
 * How the site trades freshness for speed, stored as the config object
 * {@code system.performance}: how long a page is kept in the page cache, in
 * seconds, 0 for not at all, and whether pages load the aggregated bundle of
 * the site's scripts. A site that has stored neither follows
 * {@code springdrop.cache.page-max-age} and {@code springdrop.assets.aggregate}.
 */
public record PerformanceSettings(long pageCacheMaxAge, boolean aggregateJavaScript) {

    public static final String CONFIG_NAME = "system.performance";
}
