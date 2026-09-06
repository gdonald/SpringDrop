package dev.springdrop.kernel.token;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [current-user:...]} tokens from the authenticated user of the
 * request being handled, so a message can address whoever triggered it without
 * the caller passing the user along.
 */
@Component
public class CurrentUserTokenProvider implements TokenProvider {

    private static final String ANONYMOUS_NAME = "Anonymous";

    @Override
    public String type() {
        return "current-user";
    }

    @Override
    public String resolve(String name, TokenContext context) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return switch (name) {
            case "name" -> (authentication == null) ? ANONYMOUS_NAME : authentication.getName();
            case "roles" -> (authentication == null) ? "" : roles(authentication);
            default -> null;
        };
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
                new TokenDefinition("name", "The name of the current user"),
                new TokenDefinition("roles", "The roles of the current user, comma separated"));
    }

    private static String roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.joining(", "));
    }
}
