package dev.springdrop.kernel.jsonapi;

/**
 * How JSON:API is served, stored as the config object {@code jsonapi.settings}.
 * A read-only site answers reads and refuses every create, update, and delete.
 */
public record JsonApiSettings(boolean readOnly) {

    public static final String CONFIG_NAME = "jsonapi.settings";

    public static final JsonApiSettings DEFAULTS = new JsonApiSettings(true);
}
