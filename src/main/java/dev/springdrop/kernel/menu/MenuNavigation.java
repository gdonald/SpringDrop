package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.theme.Link;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a built menu into the links the page chrome carries, so a theme draws a
 * menu without knowing anything about how menus are stored or filtered. A link
 * to a page with an alias points at the alias.
 */
@Component
public class MenuNavigation {

    private final MenuTreeBuilder trees;
    private final PathAliasManager aliases;

    public MenuNavigation(MenuTreeBuilder trees, PathAliasManager aliases) {
        this.trees = trees;
        this.aliases = aliases;
    }

    /** The main menu, with the branch leading to the given path marked. */
    public List<Link> primary(String activePath) {
        return links(trees.build(MenuConfig.MAIN, activePath).items());
    }

    public List<Link> of(String menuId, String activePath) {
        return links(trees.build(menuId, activePath).items());
    }

    private List<Link> links(List<MenuTreeItem> items) {
        return items.stream()
                .map(item -> new Link(item.link().title(), aliases.outbound(item.link().url()), links(item.children()),
                        item.inActiveTrail()))
                .toList();
    }
}
