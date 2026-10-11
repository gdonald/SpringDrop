package dev.springdrop.kernel.queue;

import tools.jackson.databind.JsonNode;

/** One item claimed from a queue: its data, and how many times processing it has failed. */
public record QueueItem(long id, String queue, JsonNode data, int attempts, String lastError) {
}
