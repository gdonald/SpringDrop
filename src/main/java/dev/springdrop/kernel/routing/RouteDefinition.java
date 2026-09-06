package dev.springdrop.kernel.routing;

/**
 * Metadata a module attaches to a route: its path pattern, a machine name, a
 * human title, whether it is an admin route, the permission required to reach it
 * (null for open routes), and whether it is an action link that must carry a
 * signed token. The access pipeline and the theme resolver consult this; the
 * controller mapping itself stays a normal Spring MVC handler.
 */
public record RouteDefinition(
        String pathPattern,
        String name,
        String title,
        boolean admin,
        String requiredPermission,
        boolean requiresLinkToken) {

    public static RouteDefinition frontEnd(String pathPattern, String name, String title) {
        return new RouteDefinition(pathPattern, name, title, false, null, false);
    }

    public static RouteDefinition admin(String pathPattern, String name, String title, String requiredPermission) {
        return new RouteDefinition(pathPattern, name, title, true, requiredPermission, false);
    }

    /**
     * An admin route reached by a link rather than a form, such as enable or
     * delete. It changes state on GET, so it is only reachable with a valid
     * action link token.
     */
    public static RouteDefinition adminAction(
            String pathPattern, String name, String title, String requiredPermission) {
        return new RouteDefinition(pathPattern, name, title, true, requiredPermission, true);
    }
}
