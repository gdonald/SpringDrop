package dev.springdrop.kernel.field;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import dev.springdrop.kernel.validation.constraints.LinkConstraint;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves the uri of a link field value. An internal link resolves to the route
 * that serves its path, so a link survives a route being renamed in one place;
 * an external one resolves to nothing, since the site knows no route for it.
 */
@Component
public class LinkResolver {

    private final RouteRegistry routeRegistry;

    public LinkResolver(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    public Optional<RouteDefinition> route(Object linkValue) {
        return uriOf(linkValue)
                .filter(uri -> uri.startsWith(LinkConstraint.INTERNAL_SCHEME))
                .flatMap(uri -> routeRegistry.match(LinkConstraint.internalPath(uri)));
    }

    public boolean isExternal(Object linkValue) {
        return uriOf(linkValue)
                .map(uri -> !uri.startsWith(LinkConstraint.INTERNAL_SCHEME))
                .orElse(false);
    }

    private static Optional<String> uriOf(Object linkValue) {
        if (linkValue instanceof Map<?, ?> link && link.get(LinkConstraint.URI_KEY) != null) {
            return Optional.of(link.get(LinkConstraint.URI_KEY).toString());
        }
        return Optional.empty();
    }
}
