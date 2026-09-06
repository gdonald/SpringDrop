package dev.springdrop.kernel.entity;

import java.util.Map;
import java.util.UUID;

/**
 * One entity as the CRUD service carries it: the keys that identify it and the
 * field values it holds in the language it was loaded or is being saved in. A
 * null id means the entity has not been saved yet; a null revision id means the
 * default revision.
 */
public record EntityData(
        String entityType,
        Object id,
        UUID uuid,
        String bundle,
        String label,
        String langcode,
        Long revisionId,
        Map<String, Object> fields) {

    public static final String DEFAULT_LANGCODE = "en";

    public EntityData {
        fields = Map.copyOf(fields);
    }

    public static EntityData of(String entityType, Object id, String bundle, String label,
            Map<String, Object> fields) {
        return new EntityData(entityType, id, null, bundle, label, DEFAULT_LANGCODE, null, fields);
    }

    public EntityData withId(Object newId) {
        return new EntityData(entityType, newId, uuid, bundle, label, langcode, revisionId, fields);
    }

    public EntityData withFields(Map<String, Object> newFields) {
        return new EntityData(entityType, id, uuid, bundle, label, langcode, revisionId, newFields);
    }

    public EntityData withLangcode(String newLangcode) {
        return new EntityData(entityType, id, uuid, bundle, label, newLangcode, revisionId, fields);
    }
}
