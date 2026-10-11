package dev.springdrop.kernel.rest;

import dev.springdrop.kernel.entity.EntityData;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An entity as REST sends and receives it: its type, id, uuid, bundle, label,
 * language, and field values. A request body may leave out any of them, which
 * leaves the entity's own value as it is.
 */
public record EntityRepresentation(String type, Object id, String uuid, String bundle, String label,
        String langcode, Map<String, Object> fields) {

    public EntityRepresentation {
        fields = fields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public static EntityRepresentation of(EntityData entity) {
        return new EntityRepresentation(entity.entityType(), entity.id(),
                entity.uuid() == null ? null : entity.uuid().toString(), entity.bundle(), entity.label(),
                entity.langcode(), entity.fields());
    }
}
