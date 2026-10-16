package dev.springdrop.kernel.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

/**
 * Refuses a web service request the security chain stops, which for these
 * endpoints is a write signed in with a session cookie that does not carry
 * the CSRF token.
 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    public static final String MESSAGE = "A write signed in with a session cookie needs the CSRF token from "
            + "/session/token in the X-CSRF-TOKEN header.";

    private final ObjectMapper objectMapper;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException refused) throws IOException {
        ApiResponses.refuse(request, response, HttpStatus.FORBIDDEN, MESSAGE, objectMapper);
    }
}
