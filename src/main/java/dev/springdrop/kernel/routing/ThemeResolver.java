package dev.springdrop.kernel.routing;

import dev.springdrop.kernel.theme.Theme;
import org.springframework.stereotype.Component;

/**
 * Chooses the theme for a request: the admin theme for admin routes (or anything
 * under the {@code /admin} prefix), and the front-end theme otherwise.
 */
@Component
public class ThemeResolver {

    public static final String ADMIN = Theme.ADMIN;
    public static final String FRONT_END = Theme.FRONT_END;

    private final RouteRegistry routeRegistry;

    public ThemeResolver(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    public String resolve(String path) {
        return routeRegistry.match(path)
                .map(route -> route.admin() ? ADMIN : FRONT_END)
                .orElseGet(() -> path.startsWith("/admin") ? ADMIN : FRONT_END);
    }
}
