package dev.springdrop.kernel.access;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import dev.springdrop.kernel.security.ActionLinkTokenService;
import dev.springdrop.support.TestActionLinkTokens;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class RouteAccessCheckerTest {

    private static final ActionLinkTokenService TOKENS = TestActionLinkTokens.service();

    private final RouteAccessChecker checker = new RouteAccessChecker(
            new RouteRegistry(List.of(
                    () -> List.of(
                            RouteDefinition.admin("/secure", "secure", "Secure", "manage things"),
                            RouteDefinition.frontEnd("/open", "open", "Open"),
                            RouteDefinition.adminAction("/enable", "enable", "Enable", "manage things")))),
            TOKENS);

    private AccessResult check(String path, String... authorities) {
        Authentication authentication = new TestingAuthenticationToken("user", "password", authorities);
        return checker.check(new MockHttpServletRequest("GET", path), authentication);
    }

    @Test
    void grantsAPermissionedRouteToAUserHoldingThePermission() {
        assertThat(check("/secure", "manage things").allowed()).isTrue();
    }

    @Test
    void deniesAPermissionedRouteToAUserWithoutThePermission() {
        assertThat(check("/secure", "something else").allowed()).isFalse();
    }

    @Test
    void aPermissionDecisionVariesByTheCallersPermissions() {
        assertThat(check("/secure", "manage things").cacheContexts())
                .containsExactly(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    @Test
    void aDecisionAboutARegisteredRouteIsTaggedWithThatRoute() {
        assertThat(check("/secure", "manage things").cacheTags()).containsExactly("route:secure");
    }

    @Test
    void anOpenRouteIsGrantedWithoutVaryingByPermissions() {
        AccessResult result = check("/open");

        assertThat(result.allowed()).isTrue();
        assertThat(result.cacheContexts()).isEmpty();
        assertThat(result.cacheTags()).containsExactly("route:open");
    }

    @Test
    void anUnregisteredPathIsGrantedWithNoCacheMetadata() {
        AccessResult result = check("/unknown");

        assertThat(result.allowed()).isTrue();
        assertThat(result.cacheContexts()).isEmpty();
        assertThat(result.cacheTags()).isEmpty();
    }

    private AccessResult checkActionLink(String token, String... authorities) {
        Authentication authentication = new TestingAuthenticationToken("user", "password", authorities);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/enable");
        if (token != null) {
            request.setParameter(ActionLinkTokenService.TOKEN_PARAMETER, token);
        }
        return checker.check(request, authentication);
    }

    @Test
    void deniesAnActionLinkWithoutAToken() {
        assertThat(checkActionLink(null, "manage things").allowed()).isFalse();
    }

    @Test
    void deniesAnActionLinkWithAForgedToken() {
        assertThat(checkActionLink("forged", "manage things").allowed()).isFalse();
    }

    @Test
    void grantsAnActionLinkCarryingTheTokenIssuedForThatUser() {
        String token = TOKENS.token("/enable", "user");

        assertThat(checkActionLink(token, "manage things").allowed()).isTrue();
    }

    @Test
    void deniesAnActionLinkToAUserWithoutThePermissionEvenWithAValidToken() {
        String token = TOKENS.token("/enable", "user");

        assertThat(checkActionLink(token, "something else").allowed()).isFalse();
    }

    @Test
    void anActionLinkDecisionIsNotCacheable() {
        String token = TOKENS.token("/enable", "user");

        assertThat(checkActionLink(token, "manage things").isCacheable()).isFalse();
    }
}
