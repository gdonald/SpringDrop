package dev.springdrop.kernel.permission;

/**
 * A permission a module declares: what it is called, what it lets someone do,
 * and whether granting it is as good as handing over the site. A restricted
 * permission is one that lets its holder reach beyond what the site's own rules
 * allow, such as running arbitrary markup, and the admin warns about it.
 */
public record PermissionDefinition(
        String name,
        String title,
        String description,
        boolean restricted,
        String provider) {

    public static PermissionDefinition of(String name, String title, String provider) {
        return new PermissionDefinition(name, title, "", false, provider);
    }

    public PermissionDefinition describedAs(String newDescription) {
        return new PermissionDefinition(name, title, newDescription, restricted, provider);
    }

    /** Marks the permission as one to be handed out only to people trusted with the site. */
    public PermissionDefinition asRestricted() {
        return new PermissionDefinition(name, title, description, true, provider);
    }
}
