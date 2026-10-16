package dev.springdrop.kernel.site;

/**
 * How the site's feeds are published, stored as the config object
 * {@code system.rss}: how many items a feed holds at most, and the
 * description each feed's channel carries, the site name when blank.
 */
public record RssSettings(int items, String description) {

    public static final String CONFIG_NAME = "system.rss";

    public static final int MAX_ITEMS = 100;

    public static final RssSettings DEFAULTS = new RssSettings(10, "");

    public RssSettings {
        description = description == null ? "" : description;
    }
}
