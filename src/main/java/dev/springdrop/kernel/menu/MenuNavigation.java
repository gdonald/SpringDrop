package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.theme.Link;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a built menu into the links the page chrome carries, so a theme draws a
 * menu without knowing anything about how menus are stored or filtered.
 */
@Component
public class MenuNavigation {

    private final MenuTreeBuilder trees;

    public MenuNavigation(MenuTreeBuilder trees) {
        this.trees = trees;
    }

    /** The main menu, with the branch leading to the given path marked. */
    public List<Link> primary(String activePath) {
        return links(trees.build(MenuConfig.MAIN, activePath).items());
    }

    public List<Link> of(String menuId, String activePath) {
        return links(trees.build(menuId, activePath).items());
    }

    private static List<Link> links(List<MenuTreeItem> items) {
        return items.stream()
                .map(item -> new Link(
                        item.link().title(), item.link().url(), links(item.children()), item.inActiveTrail()))
                .toList();
    }
}
