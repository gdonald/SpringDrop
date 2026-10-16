package dev.springdrop.kernel.security;

import dev.springdrop.kernel.jsonapi.JsonApiDocuments;
import dev.springdrop.kernel.jsonapi.JsonApiException;
import dev.springdrop.kernel.jsonapi.JsonApiResourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.ObjectMapper;

/**
 * The web service endpoints, REST under {@code /rest} and JSON:API under
 * {@code /jsonapi}, and how the security chain refuses a request to them: as
 * a JSON:API errors document under {@code /jsonapi} and as a JSON object with
 * a {@code message} under {@code /rest}. A request from someone not signed in
 * is refused with a 401 that names the bearer scheme.
 */
public final class ApiResponses {

    public static final String REST_PATH = "/rest";

    /** The challenge a 401 carries, naming the scheme a client signs in with. */
    public static final String CHALLENGE = "Bearer realm=\"SpringDrop\"";

    private static final String BEARER = "Bearer ";

    private ApiResponses() {
    }

    public static boolean isApiPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(REST_PATH + "/") || path.equals(JsonApiResourceType.BASE_PATH)
                || path.startsWith(JsonApiResourceType.BASE_PATH + "/");
    }

    /** The token an {@code Authorization: Bearer} header sends. */
    public static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                ? Optional.of(header.substring(BEARER.length()).strip()) : Optional.empty();
    }

    /** Whether the request goes to a web service endpoint signed in with a bearer token rather than a cookie. */
    public static boolean sendsBearerToken(HttpServletRequest request) {
        return isApiPath(request) && bearerToken(request).isPresent();
    }

    public static boolean anonymous(Authentication authentication) {
        return authentication instanceof AnonymousAuthenticationToken;
    }

    /** Writes a refusal in the form the endpoint answers in. */
    public static void refuse(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String message, ObjectMapper objectMapper) throws IOException {
        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE + ", error=\"invalid_token\"");
        }
        boolean jsonApi = request.getRequestURI().substring(request.getContextPath().length())
                .startsWith(JsonApiResourceType.BASE_PATH);
        response.setContentType(jsonApi ? JsonApiDocuments.MEDIA_TYPE : MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(jsonApi
                ? JsonApiDocuments.errors(status, List.of(new JsonApiException.JsonApiError(message, null)))
                : Map.of("message", message)));
    }
}
