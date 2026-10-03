package dev.springdrop.kernel.views;

import java.util.Map;

/**
 * One way a view is shown: its id within the view, the display plugin (the
 * default display, a page, a block, or a feed), its title, the options it
 * overrides, and the plugin's own settings, such as a page's path.
 */
public record ViewDisplay(String id, String plugin, String title, ViewOptions overrides,
        Map<String, Object> settings) {

    /** The display every other one takes the options it does not override from. */
    public static final String DEFAULT = "default";

    /** A display shown as a page at its path. */
    public static final String PAGE = "page";

    /** A display placed as a block. */
    public static final String BLOCK = "block";

    /** A display served as an RSS feed at its path. */
    public static final String FEED = "feed";

    /** A page or feed's path, with {@code %} for each part of it that is a contextual filter's value. */
    public static final String PATH = "path";

    /** The title of the menu link a page display adds, or blank for none. */
    public static final String MENU_TITLE = "menu_title";

    /** The menu the page display's link is in, the main menu by default. */
    public static final String MENU = "menu";

    /**
     * On the default display, whether each result is checked against its own
     * view access, on unless set to false, for a listing whose access plugin
     * already decides who may see every result, such as an admin listing.
     */
    public static final String ENTITY_ACCESS = "entity_access";

    /** Whether a page display's exposed form is drawn in a block of its own rather than above the results. */
    public static final String EXPOSED_BLOCK = "exposed_block";

    public ViewDisplay {
        settings = Map.copyOf(settings);
    }

    public String text(String name) {
        Object value = settings.get(name);
        return (value == null) ? "" : value.toString();
    }

    public boolean flag(String name) {
        return Boolean.TRUE.equals(settings.get(name)) || "true".equals(settings.get(name));
    }
}
