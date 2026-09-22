package dev.springdrop.kernel.menu;

import java.util.List;

/**
 * Contributes the menu links a module defines in code. A module registers one as
 * a bean, the same way it registers its routes, and the tree builder gathers
 * them alongside the links people added through the admin UI.
 */
@FunctionalInterface
public interface MenuLinkProvider {

    List<MenuLink> menuLinks();
}
