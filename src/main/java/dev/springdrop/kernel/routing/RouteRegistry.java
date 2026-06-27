package dev.springdrop.kernel.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Holds the route metadata contributed by every {@link RouteRegistrar} and
 * matches a request path to its definition. Patterns use Ant-style matching so a
 * module can register a prefix like {@code /admin/content/**}.
 */
@Component
public class RouteRegistry {

    private final List<RouteDefinition> routes;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RouteRegistry(List<RouteRegistrar> registrars) {
        this.routes = registrars.stream()
                .flatMap(registrar -> registrar.routes().stream())
                .toList();
    }

    public Optional<RouteDefinition> match(String path) {
        return routes.stream()
                .filter(route -> matcher.match(route.pathPattern(), path))
                .findFirst();
    }

    public List<RouteDefinition> all() {
        return routes;
    }
}
