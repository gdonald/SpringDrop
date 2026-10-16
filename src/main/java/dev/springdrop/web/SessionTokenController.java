package dev.springdrop.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The CSRF token of the caller's session, as plain text, for a client signed in
 * with a session cookie to send in the {@code X-CSRF-TOKEN} header of a write to
 * a web service endpoint.
 */
@Controller
public class SessionTokenController {

    public static final String PATH = "/session/token";

    @GetMapping(PATH)
    public ResponseEntity<String> token(CsrfToken token) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(token.getToken());
    }
}
