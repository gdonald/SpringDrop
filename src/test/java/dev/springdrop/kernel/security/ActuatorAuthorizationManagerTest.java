package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class ActuatorAuthorizationManagerTest {

    private static final RequestAuthorizationContext CONTEXT =
            new RequestAuthorizationContext(new MockHttpServletRequest("GET", "/actuator/health"));

    private boolean authorize(boolean administratorRequired, Authentication authentication) {
        Supplier<Authentication> supplier = () -> authentication;
        return new ActuatorAuthorizationManager(administratorRequired).authorize(supplier, CONTEXT).isGranted();
    }

    private Authentication anonymous() {
        return new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    }

    @Test
    void grantsAnonymousAccessWhenAnAdministratorIsNotRequired() {
        assertThat(authorize(false, anonymous())).isTrue();
    }

    @Test
    void deniesAnonymousAccessWhenAnAdministratorIsRequired() {
        assertThat(authorize(true, anonymous())).isFalse();
    }

    @Test
    void deniesAnAuthenticatedUserWithoutTheSiteAdministrationPermission() {
        Authentication editor = new TestingAuthenticationToken("editor", "password", "administer nodes");
        assertThat(authorize(true, editor)).isFalse();
    }

    @Test
    void grantsAUserHoldingTheSiteAdministrationPermission() {
        Authentication administrator = new TestingAuthenticationToken(
                "admin", "password", Permissions.ADMINISTER_SITE_CONFIGURATION);
        assertThat(authorize(true, administrator)).isTrue();
    }
}
