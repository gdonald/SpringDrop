package dev.springdrop.kernel.config.sync;

/**
 * One difference between the sync directory and the site's config: an object
 * to create, update, or delete, or one to rename from the name it has now.
 */
public record ConfigChange(Operation operation, String name, String previousName) {

    public enum Operation {
        CREATE,
        UPDATE,
        RENAME,
        DELETE
    }

    public static ConfigChange of(Operation operation, String name) {
        return new ConfigChange(operation, name, null);
    }
}
