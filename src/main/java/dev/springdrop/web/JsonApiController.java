package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.jsonapi.JsonApiDocuments;
import dev.springdrop.kernel.jsonapi.JsonApiException;
import dev.springdrop.kernel.jsonapi.JsonApiNormalizer;
import dev.springdrop.kernel.jsonapi.JsonApiQuery;
import dev.springdrop.kernel.jsonapi.JsonApiReader;
import dev.springdrop.kernel.jsonapi.JsonApiResourceType;
import dev.springdrop.kernel.jsonapi.JsonApiResourceTypes;
import dev.springdrop.kernel.jsonapi.JsonApiSettings;
import dev.springdrop.kernel.jsonapi.JsonApiWriter;
import dev.springdrop.kernel.security.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * Serves every entity type over JSON:API at
 * {@code /jsonapi/<entity type>/<bundle>}, one resource at
 * {@code /<uuid>} under it. Writes are refused while the site is read-only,
 * and a request body must be sent as {@code application/vnd.api+json}. Every
 * response, refusals included, is a JSON:API document. A refusal for someone
 * not signed in is a 401 naming the bearer scheme.
 */
@Controller
public class JsonApiController {

    public static final String PATH = JsonApiResourceType.BASE_PATH;

    private static final String PAGE_OFFSET = "page[offset]";

    private final JsonApiResourceTypes resourceTypes;
    private final JsonApiReader reader;
    private final JsonApiWriter writer;
    private final ConfigStore config;
    private final ObjectMapper objectMapper;

    public JsonApiController(JsonApiResourceTypes resourceTypes, JsonApiReader reader, JsonApiWriter writer,
            ConfigStore config, ObjectMapper objectMapper) {
        this.resourceTypes = resourceTypes;
        this.reader = reader;
        this.writer = writer;
        this.config = config;
        this.objectMapper = objectMapper;
    }

    @GetMapping(PATH)
    public ResponseEntity<String> entryPoint() {
        return answer(() -> ok(HttpStatus.OK, reader.entryPoint(base())));
    }

    @GetMapping(PATH + "/{entityType}/{bundle}")
    public ResponseEntity<String> collection(@PathVariable String entityType, @PathVariable String bundle,
            HttpServletRequest request) {
        return answer(() -> {
            Authentication who = currentAuthentication();
            JsonApiResourceType resourceType = resourceType(entityType, bundle);
            JsonApiQuery query = JsonApiQuery.parse(request.getParameterMap());
            return ok(HttpStatus.OK, reader.collection(resourceType, query, base(), link(request, null),
                    offset -> link(request, offset), who));
        });
    }

    @GetMapping(PATH + "/{entityType}/{bundle}/{id}")
    public ResponseEntity<String> individual(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String id, HttpServletRequest request) {
        return answer(() -> ok(HttpStatus.OK, reader.individual(resourceType(entityType, bundle), id,
                JsonApiQuery.parse(request.getParameterMap()), base(), link(request, null), currentAuthentication())));
    }

    @PostMapping(PATH + "/{entityType}/{bundle}")
    public ResponseEntity<String> create(@PathVariable String entityType, @PathVariable String bundle,
            @RequestHeader(name = "Content-Type", defaultValue = "") String contentType,
            @RequestBody(required = false) String body, HttpServletRequest request) {
        return answer(() -> {
            Authentication who = currentAuthentication();
            JsonApiResourceType resourceType = writable(entityType, bundle, contentType);
            EntityData created = writer.create(resourceType, body, CurrentAccount.id(), who);
            String location = base() + resourceType.path() + "/" + JsonApiNormalizer.idOf(created);
            return ResponseEntity.created(URI.create(location))
                    .contentType(MediaType.parseMediaType(JsonApiDocuments.MEDIA_TYPE))
                    .body(objectMapper.writeValueAsString(reader.document(created,
                            JsonApiQuery.parse(request.getParameterMap()), base(), location, who)));
        });
    }

    @PatchMapping(PATH + "/{entityType}/{bundle}/{id}")
    public ResponseEntity<String> update(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String id, @RequestHeader(name = "Content-Type", defaultValue = "") String contentType,
            @RequestBody(required = false) String body, HttpServletRequest request) {
        return answer(() -> {
            Authentication who = currentAuthentication();
            JsonApiResourceType resourceType = writable(entityType, bundle, contentType);
            EntityData updated = writer.update(resourceType, id, body, CurrentAccount.id(), who);
            return ok(HttpStatus.OK, reader.document(updated, JsonApiQuery.parse(request.getParameterMap()), base(),
                    link(request, null), who));
        });
    }

    @DeleteMapping(PATH + "/{entityType}/{bundle}/{id}")
    public ResponseEntity<String> delete(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String id) {
        return answer(() -> {
            Authentication who = currentAuthentication();
            JsonApiResourceType resourceType = resourceType(entityType, bundle);
            refuseWhenReadOnly();
            writer.delete(resourceType, id, who);
            return ResponseEntity.noContent().build();
        });
    }

    private ResponseEntity<String> answer(Supplier<ResponseEntity<String>> handling) {
        try {
            return handling.get();
        } catch (JsonApiException refused) {
            if (refused.status() == HttpStatus.FORBIDDEN
                    && ApiResponses.anonymous(currentAuthentication())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.WWW_AUTHENTICATE, ApiResponses.CHALLENGE)
                        .contentType(MediaType.parseMediaType(JsonApiDocuments.MEDIA_TYPE))
                        .body(objectMapper.writeValueAsString(JsonApiDocuments.errors(HttpStatus.UNAUTHORIZED,
                                refused.errors())));
            }
            return ok(refused.status(), JsonApiDocuments.errors(refused.status(), refused.errors()));
        }
    }

    private ResponseEntity<String> ok(HttpStatus status, Map<String, Object> document) {
        return ResponseEntity.status(status).contentType(MediaType.parseMediaType(JsonApiDocuments.MEDIA_TYPE))
                .body(objectMapper.writeValueAsString(document));
    }

    private JsonApiResourceType resourceType(String entityType, String bundle) {
        return resourceTypes.find(entityType, bundle).orElseThrow(() -> new JsonApiException(HttpStatus.NOT_FOUND,
                "There is no resource type " + entityType + JsonApiResourceType.SEPARATOR + bundle + "."));
    }

    private JsonApiResourceType writable(String entityType, String bundle, String contentType) {
        JsonApiResourceType resourceType = resourceType(entityType, bundle);
        refuseWhenReadOnly();
        if (!isJsonApi(contentType)) {
            throw new JsonApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "A request body is sent as "
                    + JsonApiDocuments.MEDIA_TYPE + ", without media type parameters other than a charset.");
        }
        return resourceType;
    }

    private static boolean isJsonApi(String contentType) {
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return type.equalsTypeAndSubtype(MediaType.parseMediaType(JsonApiDocuments.MEDIA_TYPE))
                    && type.getParameters().keySet().stream().allMatch(name -> name.equalsIgnoreCase("charset"));
        } catch (InvalidMediaTypeException unreadable) {
            return false;
        }
    }

    private void refuseWhenReadOnly() {
        if (config.read(JsonApiSettings.CONFIG_NAME, JsonApiSettings.class, JsonApiSettings.DEFAULTS).readOnly()) {
            throw new JsonApiException(HttpStatus.METHOD_NOT_ALLOWED,
                    "JSON:API is configured to accept only read operations.");
        }
    }

    /**
     * The person making the request, read from the security context, which
     * holds the anonymous visitor and their role's permissions too.
     */
    private static Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static String base() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    /** The request's address, with the page starting at the offset when one is given. */
    private static String link(HttpServletRequest request, Integer offset) {
        UriComponentsBuilder link = UriComponentsBuilder.fromUriString(request.getRequestURL().toString());
        request.getParameterMap().forEach((name, values) -> {
            if (offset == null || !name.equals(PAGE_OFFSET)) {
                link.queryParam(name, (Object[]) values);
            }
        });
        if (offset != null) {
            link.queryParam(PAGE_OFFSET, offset);
        }
        return link.build().encode().toUriString();
    }
}
