package dev.springdrop.kernel.entity;

import java.util.Map;
import java.util.Optional;

/**
 * Persists entities of one kind. Content storage writes base, revision, and
 * field tables; config storage writes the config store. Values are passed as a
 * map of key to value at this level, so storage stays independent of the entity
 * classes above it.
 */
public interface EntityStorage {

    /** Creates whatever the entity type needs to be stored, idempotently. */
    void install(EntityType type);

    /** Removes the storage the entity type owns. */
    void uninstall(EntityType type);

    /** An id no entity of this type holds yet, for one being created. */
    Object nextId(EntityType type);

    Optional<Map<String, Object>> load(EntityType type, Object id);

    void save(EntityType type, Object id, Map<String, Object> values);

    void delete(EntityType type, Object id);
}
