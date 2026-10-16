package dev.springdrop.kernel.config.sync;

import tools.jackson.databind.JsonNode;

/**
 * Does what saving or deleting a kind of config object involves beyond its
 * row in the config store, such as a field storage's table. An import asks
 * the handler for an object's name before it creates or deletes the object.
 */
public interface ConfigSyncHandler {

    boolean handles(String name);

    /** Sets up what a new object needs. The import then stores the object itself. */
    void create(String name, JsonNode data);

    /** Removes the object and whatever it set up. */
    void delete(String name);
}
