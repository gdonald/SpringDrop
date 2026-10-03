package dev.springdrop.kernel.search;

/**
 * A document matching a search: the entity it stands for, its title, its rank,
 * and an excerpt of its body as HTML, the matched words marked with {@code mark}.
 */
public record SearchHit(String entityType, long entityId, String langcode, String title, double rank,
        String excerpt) {
}
