package dev.springdrop.kernel.role;

import java.util.ArrayList;
import java.util.List;

/**
 * A role: what it is called, where it sits in the order roles are listed, and
 * the permissions it carries. A role marked as the administrator role carries
 * every permission the site has, whatever its own list says, so a site cannot
 * lock its owner out by forgetting to tick a box.
 */
public record RoleConfig(String id, String label, int weight, List<String> permissions, boolean administrator) {

    public static final String CONFIG_PREFIX = "user.role";

    /** The role every visitor who has not signed in has. */
    public static final String ANONYMOUS = "anonymous";

    /** The role everyone who has signed in has, on top of their own roles. */
    public static final String AUTHENTICATED = "authenticated";

    public static final String ADMINISTRATOR = "administrator";

    public RoleConfig {
        permissions = List.copyOf(permissions);
    }

    public static RoleConfig of(String id, String label, int weight) {
        return new RoleConfig(id, label, weight, List.of(), false);
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public RoleConfig granting(String permission) {
        List<String> granted = new ArrayList<>(permissions);
        if (!granted.contains(permission)) {
            granted.add(permission);
        }
        return new RoleConfig(id, label, weight, granted, administrator);
    }

    public RoleConfig revoking(String permission) {
        return new RoleConfig(id, label, weight,
                permissions.stream().filter(held -> !held.equals(permission)).toList(), administrator);
    }

    /** Marks this as the role that carries everything. */
    public RoleConfig asAdministrator() {
        return new RoleConfig(id, label, weight, permissions, true);
    }

    public boolean grants(String permission) {
        return administrator || permissions.contains(permission);
    }
}
