package dev.springdrop.kernel.jsonapi;

import java.util.List;
import org.springframework.http.HttpStatus;

/** A request JSON:API refuses, with the status it is answered with and an error object per problem. */
public class JsonApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    private final transient List<JsonApiError> errors;

    public JsonApiException(HttpStatus status, String detail) {
        this(status, List.of(new JsonApiError(detail, null)));
    }

    public JsonApiException(HttpStatus status, List<JsonApiError> errors) {
        super(errors.getFirst().detail(), null, false, false);
        this.status = status;
        this.errors = List.copyOf(errors);
    }

    public HttpStatus status() {
        return status;
    }

    public List<JsonApiError> errors() {
        return errors;
    }

    /** One problem: what is wrong and, for a request body, the JSON pointer to the member at fault. */
    public record JsonApiError(String detail, String pointer) {
    }
}
