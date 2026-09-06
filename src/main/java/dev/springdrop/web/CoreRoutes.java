package dev.springdrop.web;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.security.Permissions;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The routes core itself contributes, registered the same way a module registers
 * its own.
 */
@Component
public class CoreRoutes implements RouteRegistrar {

    @Override
    public List<RouteDefinition> routes() {
        return List.of(
                RouteDefinition.frontEnd("/", "front_page", "Home"),
                RouteDefinition.admin(
                        TokenBrowserController.PATH,
                        "token_browser",
                        "Available tokens",
                        Permissions.ADMINISTER_SITE_CONFIGURATION),
                RouteDefinition.admin(
                        FieldUiController.PATH_PREFIX + "/**",
                        "field_ui",
                        "Manage fields",
                        Permissions.ADMINISTER_FIELDS),
                RouteDefinition.admin(
                        RolePermissionsController.PATH,
                        "role_permissions",
                        "Permissions",
                        Permissions.ADMINISTER_PERMISSIONS),
                RouteDefinition.admin(
                        PeopleController.PATH + "/**",
                        "people_actions",
                        "People",
                        Permissions.ADMINISTER_USERS),
                RouteDefinition.admin(
                        IpBanController.PATH + "/**",
                        "ip_ban_actions",
                        "Banned addresses",
                        Permissions.BAN_IP_ADDRESSES),
                RouteDefinition.admin(
                        IpBanController.PATH,
                        "ip_ban",
                        "Banned addresses",
                        Permissions.BAN_IP_ADDRESSES),
                RouteDefinition.admin(
                        PeopleController.PATH,
                        "people",
                        "People",
                        Permissions.ADMINISTER_USERS));
    }
}
