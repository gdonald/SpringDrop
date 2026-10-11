package dev.springdrop.kernel.cache;

/** The names of the cache tags the site invalidates on its own. */
public final class CacheTags {

    private CacheTags() {
    }

    /** One entity, such as {@code node:42}, invalidated when it is saved or deleted. */
    public static String entity(String entityType, Object id) {
        return entityType + ":" + id;
    }

    /** Every listing of an entity type, such as {@code node_list}, invalidated when any of its entities changes. */
    public static String list(String entityType) {
        return entityType + "_list";
    }

    /**
     * The config objects under a prefix, such as {@code config_list:language.entity}, invalidated when
     * any object directly under it is saved or deleted.
     */
    public static String configList(String prefix) {
        return "config_list:" + prefix;
    }

    /** One config object, such as {@code config:system.site}, invalidated when it is saved or deleted. */
    public static String config(String name) {
        return "config:" + name;
    }
}
