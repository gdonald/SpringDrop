package dev.springdrop.kernel.jsonapi;

/**
 * One kind of resource JSON:API serves: the entities of one bundle of an entity
 * type, or of the whole type when it has no bundles. Its name is
 * {@code <entity type>--<bundle>}, such as {@code node--article}, and an
 * unbundled type repeats its own name, such as {@code user--user}. Config
 * entities are served read-only.
 */
public record JsonApiResourceType(String entityType, String bundle, boolean config) {

    public static final String BASE_PATH = "/jsonapi";

    public static final String SEPARATOR = "--";

    public String name() {
        return entityType + SEPARATOR + bundle;
    }

    /** The collection's path, under which each resource is at {@code /<id>}. */
    public String path() {
        return BASE_PATH + "/" + entityType + "/" + bundle;
    }
}
