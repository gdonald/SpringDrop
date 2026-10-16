package dev.springdrop.web;

import dev.springdrop.kernel.theme.Link;
import dev.springdrop.kernel.theme.Tab;
import java.util.List;
import java.util.Map;

/**
 * The admin pages that share a tab bar, a group of local tasks each: the
 * content listings, and the people pages. A page's tabs are those of the group
 * it belongs to, with its own marked active. A listing drawn by a view gets its
 * local actions here too, since the view knows nothing of what it lists.
 */
final class AdminLocalTasks {

    private static final List<List<Link>> GROUPS = List.of(
            List.of(new Link("Content", "/admin/content"), new Link("Comments", CommentAdminController.PATH),
                    new Link("Media", MediaController.OVERVIEW_PATH), new Link("Blocks", CustomBlockController.PATH)),
            List.of(new Link("People", PeopleController.PATH), new Link("Permissions", RolePermissionsController.PATH)));

    private AdminLocalTasks() {
    }

    private static final Map<String, List<Link>> VIEW_ACTIONS = Map.of(
            "/admin/content", List.of(new Link("Add content", NodeController.ADD_PATH)));

    static List<Link> actionsFor(String path) {
        return VIEW_ACTIONS.getOrDefault(path, List.of());
    }

    /** The tabs as the page chrome carries them. */
    static List<Tab> tabsFor(String path) {
        return forPath(path).stream().map(tab -> new Tab(tab.label(), tab.url(), tab.active())).toList();
    }

    static List<Link> forPath(String path) {
        return GROUPS.stream().filter(group -> group.stream().anyMatch(tab -> tab.url().equals(path))).findFirst()
                .map(group -> group.stream().map(tab -> tab.url().equals(path) ? new Link(tab.label(), tab.url(), List.of(), true) : tab).toList())
                .orElse(List.of());
    }
}
