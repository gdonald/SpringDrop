package dev.springdrop.kernel.rest;

import java.util.List;
import java.util.Locale;

/**
 * An entity type served over REST, stored as the config object
 * {@code rest.resource.<entity type>}: the HTTP methods it answers and the
 * formats it reads and writes.
 */
public record RestResourceConfig(String entityType, List<String> methods, List<String> formats) {

    public static final String CONFIG_PREFIX = "rest.resource";

    public static final String GET = "GET";

    public static final String POST = "POST";

    public static final String PATCH = "PATCH";

    public static final String DELETE = "DELETE";

    public static final List<String> ALL_METHODS = List.of(GET, POST, PATCH, DELETE);

    public RestResourceConfig {
        methods = List.copyOf(methods);
        formats = List.copyOf(formats);
    }

    public static String configName(String entityType) {
        return CONFIG_PREFIX + "." + entityType;
    }

    /** The permission a method of an entity type's resource needs, such as {@code restful get entity:node}. */
    public static String permission(String method, String entityType) {
        return "restful " + method.toLowerCase(Locale.ROOT) + " entity:" + entityType;
    }
}
