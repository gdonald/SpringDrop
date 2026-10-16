package dev.springdrop.kernel.jsonapi;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** The top level of a JSON:API response: its data or its errors, and the members beside them. */
public final class JsonApiDocuments {

    public static final String MEDIA_TYPE = "application/vnd.api+json";

    public static final String VERSION = "1.0";

    private JsonApiDocuments() {
    }

    public static Map<String, Object> document(Object data, List<Map<String, Object>> included,
            Map<String, Object> meta, Map<String, Object> links) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("jsonapi", Map.of("version", VERSION));
        document.put("data", data);
        if (!included.isEmpty()) {
            document.put("included", included);
        }
        if (!meta.isEmpty()) {
            document.put("meta", meta);
        }
        document.put("links", links);
        return document;
    }

    public static Map<String, Object> errors(HttpStatus status, List<JsonApiException.JsonApiError> errors) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("jsonapi", Map.of("version", VERSION));
        document.put("errors", errors.stream().map(error -> {
            Map<String, Object> object = new LinkedHashMap<>();
            object.put("status", String.valueOf(status.value()));
            object.put("title", status.getReasonPhrase());
            object.put("detail", error.detail());
            if (error.pointer() != null) {
                object.put("source", Map.of("pointer", error.pointer()));
            }
            return object;
        }).toList());
        return document;
    }

    /** A link object. */
    public static Map<String, Object> link(String href) {
        return Map.of("href", href);
    }
}
