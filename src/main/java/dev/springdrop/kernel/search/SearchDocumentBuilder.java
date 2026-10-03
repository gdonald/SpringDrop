package dev.springdrop.kernel.search;

import dev.springdrop.kernel.entity.EntityData;

/**
 * Turns entities of one type into documents for the index. A module makes an
 * entity type searchable by registering a bean of this type for it.
 */
public interface SearchDocumentBuilder {

    String entityType();

    SearchDocument build(EntityData entity);
}
