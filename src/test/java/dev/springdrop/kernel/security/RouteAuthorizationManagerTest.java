package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import dev.springdrop.support.TestActionLinkTokens;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class RouteAuthorizationManagerTest {

    private final RouteAuthorizationManager manager = new RouteAuthorizationManager(
            new RouteAccessChecker(
                    new RouteRegistry(List.of(
                            () -> List.of(
                                    RouteDefinition.admin("/secure", "secure", "Secure", "manage things"),
                                    RouteDefinition.frontEnd("/open", "open", "Open")))),
                    TestActionLinkTokens.service()));

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure");

    private boolean authorize(MockHttpServletRequest target, String... authorities) {
        Supplier<Authentication> authentication =
                () -> new TestingAuthenticationToken("user", "password", authorities);
        return manager.authorize(authentication, new RequestAuthorizationContext(target)).isGranted();
    }

    @Test
    void grantsAccessToAPermissionedRouteWhenTheUserHoldsThePermission() {
        assertThat(authorize(request, "manage things")).isTrue();
    }

    @Test
    void deniesAPermissionedRouteWhenTheUserLacksThePermission() {
        assertThat(authorize(request, "something else")).isFalse();
    }

    @Test
    void grantsAccessToAnOpenRoute() {
        assertThat(authorize(new MockHttpServletRequest("GET", "/open"))).isTrue();
    }

    @Test
    void grantsAccessToAnUnmatchedRoute() {
        assertThat(authorize(new MockHttpServletRequest("GET", "/unknown"))).isTrue();
    }

    @Test
    void leavesTheAccessResultOnTheRequestForThePageCache() {
        authorize(request, "manage things");

        AccessResult result = (AccessResult) request.getAttribute(
                RouteAuthorizationManager.ACCESS_RESULT_ATTRIBUTE);

        assertThat(result.cacheContexts()).containsExactly(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }
}
