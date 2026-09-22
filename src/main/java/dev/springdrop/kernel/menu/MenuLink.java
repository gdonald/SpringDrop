package dev.springdrop.kernel.menu;

/**
 * One link in a menu, whatever it came from: a module declared it in code, or
 * someone added it through the admin UI. The tree builder works in these alone,
 * so the two sources sit side by side in one menu.
 *
 * <p>A link names its parent by id, which is how nesting is expressed. A null
 * parent puts the link at the top of its menu. A required permission that the
 * person making the request does not hold takes the link out of the tree, along
 * with everything under it.
 */
public record MenuLink(
        String id,
        String menu,
        String title,
        String description,
        String url,
        String parent,
        int weight,
        boolean expanded,
        boolean enabled,
        String requiredPermission) {

    public static MenuLink of(String id, String menu, String title, String url) {
        return new MenuLink(id, menu, title, "", url, null, 0, false, true, null);
    }

    public MenuLink under(String parentId) {
        return new MenuLink(id, menu, title, description, url, parentId, weight, expanded, enabled,
                requiredPermission);
    }

    public MenuLink withWeight(int newWeight) {
        return new MenuLink(id, menu, title, description, url, parent, newWeight, expanded, enabled,
                requiredPermission);
    }

    public MenuLink withDescription(String newDescription) {
        return new MenuLink(id, menu, title, newDescription, url, parent, weight, expanded, enabled,
                requiredPermission);
    }

    /** Shows this link's children without the visitor opening it first. */
    public MenuLink expandedByDefault() {
        return new MenuLink(id, menu, title, description, url, parent, weight, true, enabled,
                requiredPermission);
    }

    public MenuLink disabled() {
        return new MenuLink(id, menu, title, description, url, parent, weight, expanded, false,
                requiredPermission);
    }

    public MenuLink requiring(String permission) {
        return new MenuLink(id, menu, title, description, url, parent, weight, expanded, enabled,
                permission);
    }
}
