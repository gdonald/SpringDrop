package dev.springdrop.kernel.menu;

import java.util.List;

/**
 * One link in a built tree: the link itself, the links under it, and whether it
 * lies on the trail to the page being shown. A theme walks these to draw a menu.
 */
public record MenuTreeItem(MenuLink link, List<MenuTreeItem> children, boolean inActiveTrail) {

    public MenuTreeItem {
        children = List.copyOf(children);
    }

    public static MenuTreeItem of(MenuLink link, List<MenuTreeItem> children) {
        return new MenuTreeItem(link, children, false);
    }

    public MenuTreeItem onActiveTrail() {
        return new MenuTreeItem(link, children, true);
    }

    /** Whether a visitor sees this item's children without opening it first. */
    public boolean open() {
        return link.expanded() || inActiveTrail;
    }
}
