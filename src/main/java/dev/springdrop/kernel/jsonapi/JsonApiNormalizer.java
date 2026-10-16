package dev.springdrop.kernel.jsonapi;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.FieldAccessManager;
import dev.springdrop.kernel.entity.FieldAccessRule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Entities as JSON:API resource objects. A content entity is identified by its
 * uuid and a config entity by its machine name. A resource leaves out the
 * fields the person may not view and, when the request names the fields of its
 * type, every other field.
 */
@Component
public class JsonApiNormalizer {

    private final EntityTypeManager entityTypes;
    private final EntityCrudService entities;
    private final FieldAccessManager fieldAccess;
    private final JsonApiSchema schema;
    private final JsonApiResourceTypes resourceTypes;

    public JsonApiNormalizer(EntityTypeManager entityTypes, EntityCrudService entities, FieldAccessManager fieldAccess,
            JsonApiSchema schema, JsonApiResourceTypes resourceTypes) {
        this.entityTypes = entityTypes;
        this.entities = entities;
        this.fieldAccess = fieldAccess;
        this.schema = schema;
        this.resourceTypes = resourceTypes;
    }

    public static String idOf(EntityData entity) {
        return entity.uuid() == null ? String.valueOf(entity.id()) : entity.uuid().toString();
    }

    /** A resource identifier object: the resource's type and id. */
    public Map<String, Object> identifier(EntityData entity) {
        return Map.of("type", resourceTypes.of(entity).name(), "id", idOf(entity));
    }

    public Map<String, Object> resource(EntityData entity, Set<String> sparse, String base, Authentication who) {
        EntityType type = entityTypes.require(entity.entityType());
        JsonApiResourceType resourceType = resourceTypes.of(entity);
        Map<String, String> relationshipTargets = schema.relationships(type, resourceType.bundle());

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(JsonApiSchema.INTERNAL_ID, entity.id());
        attributes.put(type.keys().label(), entity.label());
        attributes.put(type.keys().langcode(), entity.langcode());
        entity.fields().forEach((name, value) -> {
            if (!relationshipTargets.containsKey(name) && viewable(type, name, entity, who)) {
                attributes.put(name, value);
            }
        });
        Map<String, Object> relationships = new LinkedHashMap<>();
        relationshipTargets.keySet().stream().filter(name -> viewable(type, name, entity, who))
                .forEach(name -> relationships.put(name, Collections.singletonMap("data", linkage(type, entity, name))));
        if (sparse != null) {
            attributes.keySet().retainAll(sparse);
            relationships.keySet().retainAll(sparse);
        }
        return resourceObject(resourceType, idOf(entity), attributes, relationships, base);
    }

    /** A config entity, whose stored values are its attributes. */
    public Map<String, Object> configResource(JsonApiResourceType resourceType, String id, Map<String, Object> values,
            Set<String> sparse, String base) {
        Map<String, Object> attributes = configAttributes(resourceType, id, values);
        if (sparse != null) {
            attributes.keySet().retainAll(sparse);
        }
        return resourceObject(resourceType, id, attributes, Map.of(), base);
    }

    /** A config entity's attributes: its machine name as the internal id and its stored values. */
    public Map<String, Object> configAttributes(JsonApiResourceType resourceType, String id,
            Map<String, Object> values) {
        String idKey = entityTypes.require(resourceType.entityType()).keys().id();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(JsonApiSchema.INTERNAL_ID, id);
        values.forEach((name, value) -> {
            if (!name.equals(idKey)) {
                attributes.put(name, value);
            }
        });
        return attributes;
    }

    /** The entities a relationship points at, in the order it holds them, leaving out any no longer there. */
    public List<EntityData> related(EntityData entity, String relationship) {
        EntityType type = entityTypes.require(entity.entityType());
        String target = schema.relationships(type).get(relationship);
        Object value = entity.fields().get(relationship);
        List<?> ids = value == null ? List.of() : value instanceof List<?> many ? many : List.of(value);
        List<EntityData> related = new ArrayList<>();
        ids.forEach(id -> entities.load(target, ((Number) id).longValue()).ifPresent(related::add));
        return related;
    }

    private Object linkage(EntityType type, EntityData entity, String relationship) {
        List<Map<String, Object>> identifiers = related(entity, relationship).stream().map(this::identifier)
                .toList();
        if (schema.singleValued(type, relationship)) {
            return identifiers.isEmpty() ? null : identifiers.getFirst();
        }
        return identifiers;
    }

    private boolean viewable(EntityType type, String field, EntityData entity, Authentication who) {
        return fieldAccess.may(type, field, FieldAccessRule.VIEW, entity, who);
    }

    private static Map<String, Object> resourceObject(JsonApiResourceType resourceType, String id,
            Map<String, Object> attributes, Map<String, Object> relationships, String base) {
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", resourceType.name());
        resource.put("id", id);
        resource.put("attributes", attributes);
        if (!relationships.isEmpty()) {
            resource.put("relationships", relationships);
        }
        resource.put("links", Map.of("self", Map.of("href", base + resourceType.path() + "/" + id)));
        return resource;
    }
}
