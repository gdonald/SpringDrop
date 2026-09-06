package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.state.StateService;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ActionLinkTokenServiceTest {

    private static final String KEY = Base64.getEncoder().encodeToString("a-fixed-signing-key-for-tests".getBytes());

    private final StateService stateService = mock(StateService.class);

    private final ActionLinkTokenService tokens = new ActionLinkTokenService(stateService);

    private void storedKey() {
        when(stateService.get(ActionLinkTokenService.STATE_COLLECTION, ActionLinkTokenService.STATE_KEY, String.class))
                .thenReturn(Optional.of(KEY));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theSameLinkAndUserAlwaysGetTheSameToken() {
        storedKey();

        assertThat(tokens.token("/admin/modules/enable/blog", "admin"))
                .isEqualTo(tokens.token("/admin/modules/enable/blog", "admin"));
    }

    @Test
    void aDifferentLinkGetsADifferentToken() {
        storedKey();

        assertThat(tokens.token("/admin/modules/enable/blog", "admin"))
                .isNotEqualTo(tokens.token("/admin/modules/enable/forum", "admin"));
    }

    @Test
    void aDifferentUserGetsADifferentToken() {
        storedKey();

        assertThat(tokens.token("/admin/modules/enable/blog", "admin"))
                .isNotEqualTo(tokens.token("/admin/modules/enable/blog", "editor"));
    }

    @Test
    void aTokenIssuedForALinkAndUserValidates() {
        storedKey();
        String token = tokens.token("/admin/modules/enable/blog", "admin");

        assertThat(tokens.isValid("/admin/modules/enable/blog", token, "admin")).isTrue();
    }

    @Test
    void aTokenIssuedForAnotherUserIsRejected() {
        storedKey();
        String token = tokens.token("/admin/modules/enable/blog", "editor");

        assertThat(tokens.isValid("/admin/modules/enable/blog", token, "admin")).isFalse();
    }

    @Test
    void aMissingTokenIsRejected() {
        assertThat(tokens.isValid("/admin/modules/enable/blog", null, "admin")).isFalse();
    }

    @Test
    void aTokenizedPathCarriesTheTokenAsAQueryParameter() {
        storedKey();
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("admin", "password"));

        String path = tokens.tokenizedPath("/admin/modules/enable/blog");

        assertThat(path).isEqualTo("/admin/modules/enable/blog?"
                + ActionLinkTokenService.TOKEN_PARAMETER + "="
                + tokens.token("/admin/modules/enable/blog", "admin"));
    }

    @Test
    void anAnonymousRequestSignsUnderTheAnonymousIdentity() {
        storedKey();

        assertThat(tokens.token("/admin/modules/enable/blog"))
                .isEqualTo(tokens.token("/admin/modules/enable/blog", "anonymous"));
    }

    @Test
    void theSigningKeyIsGeneratedOnceAndKeptInState() {
        when(stateService.get(ActionLinkTokenService.STATE_COLLECTION, ActionLinkTokenService.STATE_KEY, String.class))
                .thenReturn(Optional.empty());

        tokens.token("/admin/modules/enable/blog", "admin");

        verify(stateService).set(
                eq(ActionLinkTokenService.STATE_COLLECTION), eq(ActionLinkTokenService.STATE_KEY), any());
    }

    @Test
    void anUnavailableSigningAlgorithmIsReportedAsAFailureToSign() {
        storedKey();
        ActionLinkTokenService broken = new ActionLinkTokenService(stateService, "NoSuchMac");

        assertThatThrownBy(() -> broken.token("/admin/modules/enable/blog", "admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sign an action link token");
    }
}
