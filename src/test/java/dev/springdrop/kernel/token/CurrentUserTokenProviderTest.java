package dev.springdrop.kernel.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserTokenProviderTest {

    private final CurrentUserTokenProvider provider = new CurrentUserTokenProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void signedInAs(String name, String... authorities) {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken(name, "password", authorities));
    }

    private String resolve(String name) {
        return provider.resolve(name, TokenContext.of(Map.of()));
    }

    @Test
    void resolvesTheNameOfTheSignedInUser() {
        signedInAs("alice");

        assertThat(resolve("name")).isEqualTo("alice");
    }

    @Test
    void resolvesTheRolesOfTheSignedInUser() {
        signedInAs("alice", "editor", "reviewer");

        assertThat(resolve("roles")).isEqualTo("editor, reviewer");
    }

    @Test
    void namesAnUnauthenticatedRequestAnonymous() {
        assertThat(resolve("name")).isEqualTo("Anonymous");
    }

    @Test
    void anUnauthenticatedRequestHasNoRoles() {
        assertThat(resolve("roles")).isEmpty();
    }

    @Test
    void returnsNothingForAnUnknownCurrentUserToken() {
        signedInAs("alice");

        assertThat(resolve("timezone")).isNull();
    }

    @Test
    void theTokenBrowserSeesEveryResolvableCurrentUserToken() {
        assertThat(provider.availableTokens()).extracting(token -> token.name()).containsExactly("name", "roles");
    }

    @Test
    void theProviderAnswersToTheCurrentUserType() {
        assertThat(provider.type()).isEqualTo("current-user");
    }
}
