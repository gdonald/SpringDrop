package dev.springdrop.kernel.routing;

import java.util.List;

/**
 * Implemented by a module to contribute route metadata. All registrars are
 * gathered by the {@link RouteRegistry} at startup.
 */
public interface RouteRegistrar {

    List<RouteDefinition> routes();
}
