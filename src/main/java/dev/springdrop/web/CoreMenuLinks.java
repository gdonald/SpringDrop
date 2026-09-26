package dev.springdrop.web;

import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkProvider;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.security.SecurityConfig;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The menu links core itself contributes, declared in code the way a module
 * declares its own. Each administration link names the permission its page
 * needs, so it is left out for someone who could not open it anyway.
 */
@Component
public class CoreMenuLinks implements MenuLinkProvider {

    public static final String HOME = "system.front_page";

    @Override
    public List<MenuLink> menuLinks() {
        return List.of(
                MenuLink.of(HOME, MenuConfig.MAIN, "Home", "/"),
                MenuLink.of("system.admin_people", MenuConfig.ADMIN, "People", PeopleController.PATH)
                        .withWeight(-10)
                        .withDescription("The accounts on this site."),
                MenuLink.of("system.admin_permissions", MenuConfig.ADMIN, "Permissions",
                                RolePermissionsController.PATH)
                        .withWeight(-5)
                        .withDescription("What each role may do."),
                MenuLink.of("system.admin_menus", MenuConfig.ADMIN, "Menus", MenuController.PATH)
                        .withDescription("The menus this site navigates by.")
                        .requiring(Permissions.ADMINISTER_MENU),
                MenuLink.of("system.admin_ban", MenuConfig.ADMIN, "Banned addresses", IpBanController.PATH)
                        .withWeight(10),
                MenuLink.of("block.admin_display", MenuConfig.ADMIN, "Block layout", BlockLayoutController.PATH)
                        .withDescription("The blocks each region of each theme shows."),
                MenuLink.of("block_content.types", MenuConfig.ADMIN, "Block types",
                                BlockContentTypeController.PATH)
                        .withDescription("The kinds of custom block, and the fields each carries."),
                MenuLink.of("block_content.library", MenuConfig.ADMIN, "Custom block library",
                                CustomBlockController.PATH)
                        .withDescription("Blocks written once and placed wherever they are wanted."),
                MenuLink.of("user.login", MenuConfig.ACCOUNT, "Sign in", SecurityConfig.LOGIN_PATH),
                MenuLink.of("user.register", MenuConfig.ACCOUNT, "Create an account",
                                AccountController.REGISTER_PATH)
                        .withWeight(5),
                MenuLink.of("user.logout", MenuConfig.ACCOUNT, "Sign out", SecurityConfig.LOGOUT_PATH)
                        .withWeight(10));
    }
}
