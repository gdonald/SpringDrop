package dev.springdrop.kernel.search;

/** Keywords to find in one entity type's documents, and which of the ranked results to give. */
public record SearchQuery(String entityType, String keywords, int offset, int limit) {
}
