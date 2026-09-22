package dev.springdrop.kernel.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

/**
 * Holds the route metadata contributed by every {@link RouteRegistrar} and
 * matches a request path to its definition. Patterns use Ant-style matching so a
 * module can register a prefix like {@code /admin/content/**}.
 *
 * <p>Where several patterns match a path, the most specific one wins rather than
 * whichever module happened to register first, so a module's own page is not
 * swallowed by another module's prefix.
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
                .min((first, second) -> matcher.getPatternComparator(path)
                        .compare(first.pathPattern(), second.pathPattern()));
    }

    public List<RouteDefinition> all() {
        return routes;
    }
}
