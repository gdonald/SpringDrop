package dev.springdrop.kernel.routing;

/**
 * Metadata a module attaches to a route: its path pattern, a machine name, a
 * human title, whether it is an admin route, and the permission required to
 * reach it (null for open routes). The access pipeline and the theme resolver
 * consult this; the controller mapping itself stays a normal Spring MVC handler.
 */
public record RouteDefinition(
        String pathPattern,
        String name,
        String title,
        boolean admin,
        String requiredPermission) {

    public static RouteDefinition frontEnd(String pathPattern, String name, String title) {
        return new RouteDefinition(pathPattern, name, title, false, null);
    }

    public static RouteDefinition admin(String pathPattern, String name, String title, String requiredPermission) {
        return new RouteDefinition(pathPattern, name, title, true, requiredPermission);
    }
}
