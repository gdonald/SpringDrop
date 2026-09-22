package dev.springdrop.kernel.menu;

/**
 * A menu: what it is called, what it is for, and whether the site may delete it.
 * A locked menu is one the site itself relies on, so the admin UI lets its links
 * be edited but not the menu removed.
 */
public record MenuConfig(String id, String label, String description, boolean locked) {

    public static final String CONFIG_PREFIX = "system.menu";

    /** The menu a visitor navigates the site with. */
    public static final String MAIN = "main";

    /** The menu behind the administration pages. */
    public static final String ADMIN = "admin";

    public static final String FOOTER = "footer";

    /** The menu holding sign in, sign out, and the account's own pages. */
    public static final String ACCOUNT = "account";

    public static MenuConfig of(String id, String label, String description) {
        return new MenuConfig(id, label, description, false);
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    /** The cache tag every rendering of this menu carries. */
    public static String cacheTag(String id) {
        return "menu:" + id;
    }

    public MenuConfig asLocked() {
        return new MenuConfig(id, label, description, true);
    }
}
