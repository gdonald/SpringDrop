package dev.springdrop.kernel.search;

/** Where indexed documents are kept and searched. */
public interface SearchBackend {

    String id();

    /** Adds the document, or replaces the one the index holds for its entity and language. */
    void index(SearchDocument document);

    /** Removes an entity's documents in every language. */
    void remove(String entityType, long entityId);

    /** Removes every document of the entity type. */
    void clear(String entityType);

    SearchResults search(SearchQuery query);
}
