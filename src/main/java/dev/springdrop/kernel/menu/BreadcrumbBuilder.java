package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import dev.springdrop.kernel.theme.Link;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The trail of links leading to the page being shown. A page that hangs in a
 * menu takes its trail from that menu, so the breadcrumb and the open menu
 * branch agree. A page that hangs in no menu falls back to its own path, naming
 * each step from the route registered for it.
 *
 * <p>Every trail starts at the front page, and the page itself is the last
 * crumb, which the theme renders as the current step rather than as a link.
 */
@Component
public class BreadcrumbBuilder {

    public static final Link HOME = new Link("Home", "/");

    private static final String ADMIN_PREFIX = "/admin";

    private final MenuTreeBuilder trees;
    private final RouteRegistry routes;

    public BreadcrumbBuilder(MenuTreeBuilder trees, RouteRegistry routes) {
        this.trees = trees;
        this.routes = routes;
    }

    public List<Link> build(String path) {
        if ("/".equals(path)) {
            return List.of(HOME);
        }

        List<Link> crumbs = new ArrayList<>();
        crumbs.add(HOME);
        crumbs.addAll(fromMenu(path).orElseGet(() -> fromPath(path)));
        return List.copyOf(crumbs);
    }

    /** The menu a path is looked up in: the admin menu for admin paths. */
    public static String menuFor(String path) {
        return path.startsWith(ADMIN_PREFIX) ? MenuConfig.ADMIN : MenuConfig.MAIN;
    }

    private Optional<List<Link>> fromMenu(String path) {
        String menuId = menuFor(path);
        List<String> trail = trees.activeTrail(menuId, path);
        if (trail.isEmpty()) {
            return Optional.empty();
        }

        Map<String, MenuLink> byId = new LinkedHashMap<>();
        flatten(trees.build(menuId).items(), byId);
        return Optional.of(trail.stream()
                .map(byId::get)
                .map(link -> new Link(link.title(), link.url()))
                .toList());
    }

    private static void flatten(List<MenuTreeItem> items, Map<String, MenuLink> into) {
        for (MenuTreeItem item : items) {
            into.put(item.link().id(), item.link());
            flatten(item.children(), into);
        }
    }

    /**
     * A trail read off the path: every prefix of it that names a registered
     * route becomes a crumb titled the way that route is titled.
     */
    private List<Link> fromPath(String path) {
        List<Link> crumbs = new ArrayList<>();
        StringBuilder prefix = new StringBuilder();
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            prefix.append('/').append(segment);
            String step = prefix.toString();
            routes.match(step)
                    .map(RouteDefinition::title)
                    .ifPresent(title -> crumbs.add(new Link(title, step)));
        }
        return crumbs;
    }
}
