package dev.springdrop.web;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.rest.EntityRepresentation;
import dev.springdrop.kernel.rest.RestEntityWriter;
import dev.springdrop.kernel.rest.RestFieldValues;
import dev.springdrop.kernel.rest.RestResourceConfig;
import dev.springdrop.kernel.rest.RestResourceManager;
import dev.springdrop.kernel.rest.RestSerializer;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.databind.ObjectMapper;

/**
 * Serves content entities over REST, for the entity types and methods the
 * REST resources enable. Each request needs the method's permission and the
 * entity access the method stands for: view to read, create to add, update
 * to change, and delete to remove. A response is in the format the
 * {@code _format} parameter names, JSON unless it names another the resource
 * enables, and a request body is read in the format its content type names.
 * A refusal is answered as a JSON object with a {@code message}.
 */
@Controller
public class EntityRestController {

    public static final String PATH = "/rest";

    public static final String FORMAT = "_format";

    private final RestResourceManager resources;
    private final EntityTypeManager entityTypes;
    private final EntityCrudService entities;
    private final EntityAccessManager access;
    private final RestFieldValues fieldValues;
    private final RestEntityWriter writer;
    private final ObjectMapper objectMapper;

    public EntityRestController(RestResourceManager resources, EntityTypeManager entityTypes, EntityCrudService entities,
            EntityAccessManager access, RestFieldValues fieldValues, RestEntityWriter writer,
            ObjectMapper objectMapper) {
        this.resources = resources;
        this.entityTypes = entityTypes;
        this.entities = entities;
        this.access = access;
        this.fieldValues = fieldValues;
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    public static String entityPath(String entityType, Object id) {
        return PATH + "/" + entityType + "/" + id;
    }

    /** A refusal: the status it is answered with and what it says. */
    private record Refusal(HttpStatus status, String message, List<Map<String, String>> errors) {

        Refusal(HttpStatus status, String message) {
            this(status, message, List.of());
        }
    }

    /** What a request turned out to be: a refusal, or what it may go on with. */
    private static final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final transient Refusal refusal;

        Refused(Refusal refusal) {
            super(refusal.message(), null, false, false);
            this.refusal = refusal;
        }
    }

    @GetMapping(PATH + "/{entityType}/{id}")
    public ResponseEntity<String> read(@PathVariable String entityType, @PathVariable long id,
            @RequestParam(name = FORMAT, defaultValue = "json") String format, Authentication who) {
        return answer(() -> {
            RestResourceConfig resource = resource(entityType, RestResourceConfig.GET, who);
            RestSerializer serializer = responseSerializer(resource, format);
            EntityData entity = found(entityType, id);
            permitted(entityType, entity, EntityAccessHandler.VIEW, who);
            return body(HttpStatus.OK, serializer, entity);
        });
    }

    @PostMapping(PATH + "/{entityType}")
    public ResponseEntity<String> create(@PathVariable String entityType,
            @RequestParam(name = FORMAT, defaultValue = "json") String format,
            @RequestHeader(name = "Content-Type", defaultValue = "") String contentType,
            @RequestBody(required = false) String body, Authentication who) {
        return answer(() -> {
            RestResourceConfig resource = resource(entityType, RestResourceConfig.POST, who);
            RestSerializer responses = responseSerializer(resource, format);
            EntityRepresentation submitted = read(resource, contentType, body);
            EntityType type = entityTypes.require(entityType);
            String bundle = type.keys().bundle() == null ? null : submitted.bundle();
            if (type.keys().bundle() != null && (bundle == null || bundle.isBlank())) {
                throw new Refused(new Refusal(HttpStatus.UNPROCESSABLE_CONTENT, "The bundle is required."));
            }
            permitted(entityType, bundle == null ? entityType : bundle, EntityAccessHandler.CREATE, who);
            Map<String, Object> values = new LinkedHashMap<>(writable(type, bundle, submitted.fields()));
            if (type.baseFields().stream().anyMatch(field -> field.name().equals(BaseFieldDefinition.OWNER))) {
                values.put(BaseFieldDefinition.OWNER, CurrentAccount.id());
            }
            EntityData saved = save(EntityData.of(entityType, null, bundle,
                    submitted.label() == null ? "" : submitted.label(), values)
                    .withLangcode(submitted.langcode() == null ? EntityData.DEFAULT_LANGCODE : submitted.langcode()));
            return ResponseEntity.created(URI.create(entityPath(entityType, saved.id())))
                    .contentType(MediaType.parseMediaType(responses.mediaType()))
                    .body(responses.serialize(EntityRepresentation.of(saved)));
        });
    }

    @PatchMapping(PATH + "/{entityType}/{id}")
    public ResponseEntity<String> update(@PathVariable String entityType, @PathVariable long id,
            @RequestParam(name = FORMAT, defaultValue = "json") String format,
            @RequestHeader(name = "Content-Type", defaultValue = "") String contentType,
            @RequestBody(required = false) String body, Authentication who) {
        return answer(() -> {
            RestResourceConfig resource = resource(entityType, RestResourceConfig.PATCH, who);
            RestSerializer responses = responseSerializer(resource, format);
            EntityRepresentation submitted = read(resource, contentType, body);
            EntityData entity = found(entityType, id);
            permitted(entityType, entity, EntityAccessHandler.UPDATE, who);
            Map<String, Object> values = new LinkedHashMap<>(entity.fields());
            values.putAll(writable(entityTypes.require(entityType), entity.bundle(), submitted.fields()));
            EntityData changed = new EntityData(entityType, entity.id(), entity.uuid(), entity.bundle(),
                    submitted.label() == null ? entity.label() : submitted.label(), entity.langcode(), null, values);
            return body(HttpStatus.OK, responses, save(changed));
        });
    }

    @DeleteMapping(PATH + "/{entityType}/{id}")
    public ResponseEntity<String> delete(@PathVariable String entityType, @PathVariable long id,
            Authentication who) {
        return answer(() -> {
            resource(entityType, RestResourceConfig.DELETE, who);
            EntityData entity = found(entityType, id);
            permitted(entityType, entity, EntityAccessHandler.DELETE, who);
            writer.delete(entityType, id);
            return ResponseEntity.noContent().build();
        });
    }

    private ResponseEntity<String> answer(Supplier<ResponseEntity<String>> handling) {
        try {
            return handling.get();
        } catch (Refused refused) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", refused.refusal.message());
            if (!refused.refusal.errors().isEmpty()) {
                body.put("errors", refused.refusal.errors());
            }
            return ResponseEntity.status(refused.refusal.status()).contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body));
        }
    }

    /** The resource, when it enables the method and the person holds the method's permission. */
    private RestResourceConfig resource(String entityType, String method, Authentication who) {
        RestResourceConfig resource = resources.find(entityType)
                .filter(found -> found.methods().contains(method))
                .filter(found -> entityTypes.all().stream().anyMatch(type -> type.id().equals(entityType)
                        && type.kind() == EntityKind.CONTENT))
                .orElseThrow(() -> new Refused(new Refusal(HttpStatus.NOT_FOUND,
                        "No REST resource answers " + method + " for " + entityType + ".")));
        String permission = RestResourceConfig.permission(method, entityType);
        if (!AccountPrincipals.bypassesChecks(who) && who.getAuthorities().stream()
                .noneMatch(authority -> authority.getAuthority().equals(permission))) {
            throw new Refused(new Refusal(HttpStatus.FORBIDDEN, "The " + permission + " permission is required."));
        }
        return resource;
    }

    private RestSerializer responseSerializer(RestResourceConfig resource, String format) {
        return Optional.of(format).filter(resource.formats()::contains).flatMap(resources::serializer)
                .orElseThrow(() -> new Refused(new Refusal(HttpStatus.NOT_ACCEPTABLE,
                        "The resource is not served as " + format + ".")));
    }

    /** The body, read in the enabled format whose media type the content type names. */
    private EntityRepresentation read(RestResourceConfig resource, String contentType, String body) {
        RestSerializer serializer = resource.formats().stream().flatMap(format -> resources.serializer(format).stream())
                .filter(each -> contentType.startsWith(each.mediaType())).findFirst()
                .orElseThrow(() -> new Refused(new Refusal(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        "The resource does not read " + contentType + ".")));
        try {
            return serializer.deserialize(body == null ? "" : body);
        } catch (RestSerializer.MalformedBodyException malformed) {
            throw new Refused(new Refusal(HttpStatus.BAD_REQUEST, malformed.getMessage()));
        }
    }

    private EntityData found(String entityType, long id) {
        return entities.load(entityType, id).orElseThrow(() -> new Refused(new Refusal(HttpStatus.NOT_FOUND,
                "No " + entityType + " " + id + ".")));
    }

    private void permitted(String entityType, Object entity, String operation, Authentication who) {
        if (!access.check(entityType, entity, operation, who).allowed()) {
            throw new Refused(new Refusal(HttpStatus.FORBIDDEN, "You may not " + operation + " this " + entityType
                    + "."));
        }
    }

    private Map<String, Object> writable(EntityType type, String bundle, Map<String, Object> submitted) {
        RestFieldValues.Result result = fieldValues.writable(type, bundle, submitted);
        if (result.error() != null) {
            throw new Refused(new Refusal(HttpStatus.UNPROCESSABLE_CONTENT, result.error()));
        }
        return result.values();
    }

    private EntityData save(EntityData entity) {
        try {
            return writer.save(entity, CurrentAccount.id());
        } catch (EntityValidationException invalid) {
            throw new Refused(new Refusal(HttpStatus.UNPROCESSABLE_CONTENT, "The entity is not valid.",
                    invalid.violations().stream().map(violation -> Map.of("field", violation.propertyPath(),
                            "message", violation.message())).toList()));
        }
    }

    private static ResponseEntity<String> body(HttpStatus status, RestSerializer serializer, EntityData entity) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(serializer.mediaType()))
                .body(serializer.serialize(EntityRepresentation.of(entity)));
    }
}
