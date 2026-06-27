package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

class RouteAuthorizationManagerTest {

    private final RouteAuthorizationManager manager = new RouteAuthorizationManager(new RouteRegistry(List.of(
            () -> List.of(
                    RouteDefinition.admin("/secure", "secure", "Secure", "manage things"),
                    RouteDefinition.frontEnd("/open", "open", "Open")))));

    private boolean authorize(String path, String... authorities) {
        Supplier<Authentication> authentication =
                () -> new TestingAuthenticationToken("user", "password", authorities);
        RequestAuthorizationContext context =
                new RequestAuthorizationContext(new MockHttpServletRequest("GET", path));
        return manager.authorize(authentication, context).isGranted();
    }

    @Test
    void grantsAccessToAPermissionedRouteWhenTheUserHoldsThePermission() {
        assertThat(authorize("/secure", "manage things")).isTrue();
    }

    @Test
    void deniesAPermissionedRouteWhenTheUserLacksThePermission() {
        assertThat(authorize("/secure", "something else")).isFalse();
    }

    @Test
    void grantsAccessToAnOpenRoute() {
        assertThat(authorize("/open")).isTrue();
    }

    @Test
    void grantsAccessToAnUnmatchedRoute() {
        assertThat(authorize("/unknown")).isTrue();
    }
}
