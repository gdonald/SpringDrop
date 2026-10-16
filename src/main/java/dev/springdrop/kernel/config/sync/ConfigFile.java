package dev.springdrop.kernel.config.sync;

import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * One config object as an export writes it, to {@code <name>.yml}: its uuid,
 * its dependencies, and its value under {@code data}.
 */
public record ConfigFile(String name, UUID uuid, ConfigDependencies dependencies, JsonNode data) {
}
