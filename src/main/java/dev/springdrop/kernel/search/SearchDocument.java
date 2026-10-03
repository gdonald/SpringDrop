package dev.springdrop.kernel.search;

/** What the index holds for one entity in one language: its title and the text of its body. */
public record SearchDocument(String entityType, long entityId, String langcode, String title, String body) {
}
