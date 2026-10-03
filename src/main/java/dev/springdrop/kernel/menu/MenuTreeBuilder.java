package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.cache.CacheTags;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds a menu into the nested, ordered tree a theme draws. Links come from two
 * places at once: the ones modules declare in code and the ones a site wrote
 * through the admin UI.
 *
 * <p>A link is left out when it is disabled, when its own required permission is
 * not held, or when the route it points at requires a permission that is not
 * held. Leaving a link out takes everything under it with it, since a branch
 * whose root is out of reach leads nowhere.
 *
 * <p>The tree is ordered by weight and then by title, so two links of equal
 * weight come out in a stable order rather than the order they were gathered in.
 */
@Component
public class MenuTreeBuilder {

    private final List<MenuLinkProvider> providers;
    private final MenuLinkContentService contentLinks;
    private final MenuPermissionChecker permissions;
    private final RouteRegistry routes;

    public MenuTreeBuilder(
            List<MenuLinkProvider> providers,
            MenuLinkContentService contentLinks,
            MenuPermissionChecker permissions,
            RouteRegistry routes) {
        this.providers = providers;
        this.contentLinks = contentLinks;
        this.permissions = permissions;
        this.routes = routes;
    }

    public MenuTree build(String menuId) {
        return build(menuId, null);
    }

    /**
     * The tree for a menu, with the trail to the given path marked. A null path
     * marks nothing, which is what a menu rendered outside a request does.
     */
    public MenuTree build(String menuId, String activePath) {
        List<MenuLink> reachable = reachable(gather(menuId));
        List<MenuTreeItem> items = nest(reachable, null, effectiveParents(reachable));
        List<String> trail = trailTo(reachable, activePath);
        return new MenuTree(menuId, markTrail(items, trail), cacheability(menuId));
    }

    /**
     * Every link in a menu, disabled and inaccessible ones included. This is the
     * admin's view of a menu, where a link has to be visible to be turned back
     * on.
     */
    public MenuTree buildForAdministration(String menuId) {
        List<MenuLink> links = gather(menuId);
        return new MenuTree(menuId, nest(links, null, effectiveParents(links)), cacheability(menuId));
    }

    /** The ids on the trail from the top of the menu to the link for a path. */
    public List<String> activeTrail(String menuId, String activePath) {
        return trailTo(reachable(gather(menuId)), activePath);
    }

    /** What any built tree of a menu depends on. */
    public static CacheMetadata cacheability(String menuId) {
        return CacheMetadata.EMPTY
                .withTag(MenuConfig.cacheTag(menuId))
                .withTag(CacheTags.config(MenuConfig.configName(menuId)))
                .withTag(CacheTags.list(MenuLinkContentEntityType.ID))
                .withContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    private List<MenuLink> gather(String menuId) {
        List<MenuLink> links = new ArrayList<>();
        for (MenuLinkProvider provider : providers) {
            provider.menuLinks().stream()
                    .filter(link -> menuId.equals(link.menu()))
                    .forEach(links::add);
        }
        links.addAll(contentLinks.inMenu(menuId));
        return links;
    }

    /**
     * The links that survive access filtering, keeping a child only while every
     * link between it and the top of the menu survived too.
     */
    private List<MenuLink> reachable(List<MenuLink> links) {
        Map<String, MenuLink> byId = new LinkedHashMap<>();
        links.forEach(link -> byId.put(link.id(), link));

        List<MenuLink> allowed = new ArrayList<>();
        for (MenuLink link : links) {
            if (visible(link) && ancestorsVisible(byId, link)) {
                allowed.add(link);
            }
        }
        return allowed;
    }

    private boolean ancestorsVisible(Map<String, MenuLink> byId, MenuLink link) {
        Set<String> seen = new LinkedHashSet<>();
        String parentId = link.parent();
        while (parentId != null && seen.add(parentId)) {
            MenuLink parent = byId.get(parentId);
            if (parent == null) {
                return true;
            }
            if (!visible(parent)) {
                return false;
            }
            parentId = parent.parent();
        }
        return true;
    }

    private boolean visible(MenuLink link) {
        if (!link.enabled()) {
            return false;
        }
        if (link.requiredPermission() != null && !permissions.holds(link.requiredPermission())) {
            return false;
        }
        return routes.match(link.url())
                .map(RouteDefinition::requiredPermission)
                .map(permissions::holds)
                .orElse(true);
    }

    /**
     * A link whose parent is not in the menu hangs at the top rather than
     * vanishing, so a link that outlives the one it hung under stays reachable.
     * A link that is its own ancestor hangs at the top too, since a ring of
     * links has no top of its own.
     */
    private static List<MenuTreeItem> nest(List<MenuLink> links, String parentId,
            Map<String, String> parents) {

        return links.stream()
                .filter(link -> Objects.equals(parents.get(link.id()), parentId))
                .sorted(Comparator.comparingInt(MenuLink::weight).thenComparing(MenuLink::title))
                .map(link -> MenuTreeItem.of(link, nest(links, link.id(), parents)))
                .toList();
    }

    /** The parent each link actually hangs under, once gaps and rings are resolved. */
    private static Map<String, String> effectiveParents(List<MenuLink> links) {
        Map<String, MenuLink> byId = new LinkedHashMap<>();
        links.forEach(link -> byId.put(link.id(), link));

        Map<String, String> parents = new LinkedHashMap<>();
        for (MenuLink link : links) {
            parents.put(link.id(), byId.containsKey(link.parent()) && !encircles(byId, link)
                    ? link.parent()
                    : null);
        }
        return parents;
    }

    /** Whether following this link's parents leads back to the link itself. */
    private static boolean encircles(Map<String, MenuLink> byId, MenuLink link) {
        Set<String> seen = new LinkedHashSet<>();
        MenuLink above = byId.get(link.parent());
        while (above != null && seen.add(above.id())) {
            if (above.id().equals(link.id())) {
                return true;
            }
            above = byId.get(above.parent());
        }
        return false;
    }

    private static List<String> trailTo(List<MenuLink> links, String activePath) {
        if (activePath == null) {
            return List.of();
        }
        Map<String, MenuLink> byId = new LinkedHashMap<>();
        links.forEach(link -> byId.put(link.id(), link));

        MenuLink active = links.stream()
                .filter(link -> activePath.equals(link.url()))
                .findFirst()
                .orElse(null);

        List<String> trail = new ArrayList<>();
        while (active != null && !trail.contains(active.id())) {
            trail.addFirst(active.id());
            active = byId.get(active.parent());
        }
        return List.copyOf(trail);
    }

    private static List<MenuTreeItem> markTrail(List<MenuTreeItem> items, List<String> trail) {
        return items.stream()
                .map(item -> {
                    MenuTreeItem marked = MenuTreeItem.of(item.link(), markTrail(item.children(), trail));
                    return trail.contains(item.link().id()) ? marked.onActiveTrail() : marked;
                })
                .toList();
    }
}
