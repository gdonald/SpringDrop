package dev.springdrop.kernel.jsonapi;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.FieldAccessManager;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.rest.RestEntityWriter;
import dev.springdrop.kernel.rest.RestFieldValues;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Answers JSON:API writes to content entities. A body is a document whose
 * {@code data} is a resource object of the endpoint's type. Attributes write
 * fields and the label and language, and relationships write the reference
 * fields by the uuids of the entities they point at. Each write needs the
 * entity access it stands for and edit access to every field it names, and the
 * entity is validated before it is saved. A null value or an empty
 * relationship clears a configurable field.
 */
@Component
public class JsonApiWriter {

    private final EntityTypeManager entityTypes;
    private final EntityCrudService entities;
    private final EntityAccessManager access;
    private final FieldAccessManager fieldAccess;
    private final JsonApiSchema schema;
    private final JsonApiReader reader;
    private final JsonApiResourceTypes resourceTypes;
    private final RestFieldValues fieldValues;
    private final RestEntityWriter writer;
    private final ObjectMapper objectMapper;

    public JsonApiWriter(EntityTypeManager entityTypes, EntityCrudService entities, EntityAccessManager access,
            FieldAccessManager fieldAccess, JsonApiSchema schema, JsonApiReader reader,
            JsonApiResourceTypes resourceTypes, RestFieldValues fieldValues, RestEntityWriter writer,
            ObjectMapper objectMapper) {
        this.entityTypes = entityTypes;
        this.entities = entities;
        this.access = access;
        this.fieldAccess = fieldAccess;
        this.schema = schema;
        this.reader = reader;
        this.resourceTypes = resourceTypes;
        this.fieldValues = fieldValues;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    /** What a body's resource object asks for. */
    private record Submitted(String label, String langcode, Map<String, Object> attributes,
            Map<String, JsonNode> relationships) {
    }

    public EntityData create(JsonApiResourceType resourceType, String body, long accountId, Authentication who) {
        refuseConfig(resourceType);
        EntityType type = entityTypes.require(resourceType.entityType());
        Submitted submitted = read(type, resourceType, body, null);
        String bundle = type.keys().bundle() == null ? null : resourceType.bundle();
        permitted(type.id(), bundle == null ? type.id() : bundle, EntityAccessHandler.CREATE, who);
        Map<String, Object> values = new LinkedHashMap<>();
        apply(type, resourceType.bundle(), EntityData.of(type.id(), null, bundle, "", Map.of()), submitted, values,
                who);
        if (type.baseFields().stream().anyMatch(field -> field.name().equals(BaseFieldDefinition.OWNER))) {
            values.put(BaseFieldDefinition.OWNER, accountId);
        }
        EntityData saved = save(EntityData.of(type.id(), null, bundle,
                submitted.label() == null ? "" : submitted.label(), values)
                .withLangcode(submitted.langcode() == null ? EntityData.DEFAULT_LANGCODE : submitted.langcode()),
                accountId);
        return entities.load(type.id(), saved.id(), saved.langcode(), null).orElseThrow();
    }

    public EntityData update(JsonApiResourceType resourceType, String id, String body, long accountId,
            Authentication who) {
        refuseConfig(resourceType);
        EntityType type = entityTypes.require(resourceType.entityType());
        EntityData entity = reader.find(resourceType, id);
        Submitted submitted = read(type, resourceType, body, id);
        permitted(type.id(), entity, EntityAccessHandler.UPDATE, who);
        if (submitted.langcode() != null && !submitted.langcode().equals(entity.langcode())) {
            throw unprocessable("The language of a saved entity cannot be changed.",
                    "/data/attributes/" + type.keys().langcode());
        }
        Map<String, Object> values = new LinkedHashMap<>(entity.fields());
        apply(type, resourceType.bundle(), entity, submitted, values, who);
        save(new EntityData(type.id(), entity.id(), entity.uuid(), entity.bundle(),
                submitted.label() == null ? entity.label() : submitted.label(), entity.langcode(), null, values),
                accountId);
        return entities.load(type.id(), entity.id(), entity.langcode(), null).orElseThrow();
    }

    public void delete(JsonApiResourceType resourceType, String id, Authentication who) {
        refuseConfig(resourceType);
        EntityData entity = reader.find(resourceType, id);
        permitted(entity.entityType(), entity, EntityAccessHandler.DELETE, who);
        writer.delete(entity.entityType(), ((Number) entity.id()).longValue());
    }

    private static void refuseConfig(JsonApiResourceType resourceType) {
        if (resourceType.config()) {
            throw new JsonApiException(HttpStatus.METHOD_NOT_ALLOWED, resourceType.name()
                    + " is a config entity type, which JSON:API serves read-only.");
        }
    }

    private void permitted(String entityType, Object entity, String operation, Authentication who) {
        if (!access.check(entityType, entity, operation, who).allowed()) {
            throw new JsonApiException(HttpStatus.FORBIDDEN, "You may not " + operation + " this " + entityType + ".");
        }
    }

    private Submitted read(EntityType type, JsonApiResourceType resourceType, String body, String expectedId) {
        JsonNode data;
        try {
            data = objectMapper.readTree(body == null ? "" : body).get("data");
        } catch (JacksonException unreadable) {
            throw new JsonApiException(HttpStatus.BAD_REQUEST, "The request body is not JSON.");
        }
        if (data == null || !data.isObject()) {
            throw new JsonApiException(HttpStatus.BAD_REQUEST, "The request body needs a data object.");
        }
        String submittedType = data.path("type").asString("");
        if (!submittedType.equals(resourceType.name())) {
            throw new JsonApiException(HttpStatus.CONFLICT, "The resource type " + submittedType
                    + " does not match the endpoint's " + resourceType.name() + ".");
        }
        if (expectedId == null && data.has("id")) {
            throw new JsonApiException(HttpStatus.FORBIDDEN, "Ids are given by the site, not the client.");
        }
        if (expectedId != null && !data.path("id").asString("").equals(expectedId)) {
            throw new JsonApiException(HttpStatus.CONFLICT, "The resource id " + data.path("id").asString("")
                    + " does not match the endpoint's " + expectedId + ".");
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        members(data, "attributes").forEach((name, value) -> attributes.put(name,
                objectMapper.treeToValue(value, Object.class)));
        Map<String, JsonNode> relationships = new LinkedHashMap<>();
        members(data, "relationships").forEach((name, relationship) -> {
            if (!relationship.isObject() || !relationship.has("data")) {
                throw new JsonApiException(HttpStatus.BAD_REQUEST, "The relationship " + name
                        + " needs a data member.");
            }
            relationships.put(name, relationship.get("data"));
        });
        if (attributes.containsKey(JsonApiSchema.INTERNAL_ID)) {
            throw unprocessable("The attribute " + JsonApiSchema.INTERNAL_ID + " cannot be written.",
                    "/data/attributes/" + JsonApiSchema.INTERNAL_ID);
        }
        Object label = attributes.remove(type.keys().label());
        Object langcode = attributes.remove(type.keys().langcode());
        return new Submitted(label == null ? null : String.valueOf(label),
                langcode == null ? null : String.valueOf(langcode), attributes, relationships);
    }

    private static Map<String, JsonNode> members(JsonNode data, String name) {
        JsonNode node = data.get(name);
        if (node == null) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw new JsonApiException(HttpStatus.BAD_REQUEST, "The " + name + " member is not an object.");
        }
        Map<String, JsonNode> members = new LinkedHashMap<>();
        node.properties().forEach(member -> members.put(member.getKey(), member.getValue()));
        return members;
    }

    /**
     * Puts the submitted fields into the values: each one checked for edit
     * access, relationships resolved to ids, and every value read as its column
     * takes it. A cleared field is written with no values.
     */
    private void apply(EntityType type, String bundle, EntityData entity, Submitted submitted,
            Map<String, Object> values, Authentication who) {
        submitted.attributes().keySet().forEach(name -> editable(type, name, entity, "attributes", who));
        submitted.relationships().keySet().forEach(name -> editable(type, name, entity, "relationships", who));

        Map<String, String> relationships = schema.relationships(type, bundle);
        Map<String, Object> written = new LinkedHashMap<>();
        Set<String> cleared = new LinkedHashSet<>();
        submitted.attributes().forEach((name, value) -> {
            if (relationships.containsKey(name)) {
                throw unprocessable("The field " + name + " is a relationship, written under relationships.",
                        "/data/attributes/" + name);
            }
            if (value == null) {
                cleared.add(name);
            } else {
                written.put(name, value);
            }
        });
        submitted.relationships().forEach((name, data) -> {
            String target = relationships.get(name);
            if (target == null) {
                throw unprocessable("There is no relationship " + name + ".", "/data/relationships/" + name);
            }
            List<Object> ids = resolve(name, target, data);
            if (ids.isEmpty()) {
                cleared.add(name);
            } else {
                written.put(name, schema.singleValued(type, name) ? ids.getFirst() : ids);
            }
        });

        List<String> attributes = schema.attributes(type, bundle);
        for (String name : cleared) {
            boolean configurable = (attributes.contains(name) || relationships.containsKey(name))
                    && type.baseFields().stream().noneMatch(field -> field.name().equals(name));
            if (!configurable) {
                throw unprocessable("The field " + name + " cannot be cleared.", pointer(relationships, name));
            }
        }
        RestFieldValues.Result result = fieldValues.writable(type, bundle, written);
        if (result.error() != null) {
            throw unprocessable(result.error(), null);
        }
        values.putAll(result.values());
        cleared.forEach(name -> values.put(name, List.of()));
    }

    private void editable(EntityType type, String name, EntityData entity, String member, Authentication who) {
        if (!fieldAccess.may(type, name, FieldAccessRule.EDIT, entity, who)) {
            throw new JsonApiException(HttpStatus.FORBIDDEN, List.of(new JsonApiException.JsonApiError(
                    "You may not edit the field " + name + ".", "/data/" + member + "/" + name)));
        }
    }

    /** The ids of the entities a relationship's data points at, each checked to be of the type it claims. */
    private List<Object> resolve(String name, String target, JsonNode data) {
        if (data.isNull()) {
            return List.of();
        }
        List<JsonNode> identifiers = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(identifiers::add);
        } else {
            identifiers.add(data);
        }
        List<Object> ids = new ArrayList<>();
        for (JsonNode identifier : identifiers) {
            String claimedType = identifier.path("type").asString("");
            String id = identifier.path("id").asString("");
            Optional<EntityData> found = reader.idsOf(target, List.of(id)).stream().findFirst()
                    .flatMap(each -> entities.load(target, each))
                    .filter(entity -> resourceTypes.of(entity).name().equals(claimedType));
            if (found.isEmpty()) {
                throw unprocessable("There is no " + (claimedType.isEmpty() ? target : claimedType) + " " + id
                        + " for the relationship " + name + ".", "/data/relationships/" + name);
            }
            ids.add(found.get().id());
        }
        return ids;
    }

    private EntityData save(EntityData entity, long accountId) {
        try {
            return writer.save(entity, accountId);
        } catch (EntityValidationException invalid) {
            throw new JsonApiException(HttpStatus.UNPROCESSABLE_CONTENT, invalid.violations().stream()
                    .map(violation -> {
                        String field = violation.propertyPath().replaceFirst("^fields\\.", "").split("[.\\[]")[0];
                        return new JsonApiException.JsonApiError(field + ": " + violation.message(),
                                "/data/attributes/" + field);
                    })
                    .toList());
        }
    }

    private static String pointer(Map<String, String> relationships, String name) {
        return (relationships.containsKey(name) ? "/data/relationships/" : "/data/attributes/") + name;
    }

    private static JsonApiException unprocessable(String detail, String pointer) {
        return new JsonApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                List.of(new JsonApiException.JsonApiError(detail, pointer)));
    }
}
