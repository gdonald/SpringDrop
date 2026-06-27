package dev.springdrop.kernel.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ThemeResolverTest {

    private final ThemeResolver resolver = new ThemeResolver(new RouteRegistry(List.of(
            () -> List.of(
                    RouteDefinition.admin("/dashboard", "dashboard", "Dashboard", null),
                    RouteDefinition.frontEnd("/articles", "articles", "Articles")))));

    @Test
    void usesTheAdminThemeForAMatchedAdminRoute() {
        assertThat(resolver.resolve("/dashboard")).isEqualTo(ThemeResolver.ADMIN);
    }

    @Test
    void usesTheFrontEndThemeForAMatchedFrontEndRoute() {
        assertThat(resolver.resolve("/articles")).isEqualTo(ThemeResolver.FRONT_END);
    }

    @Test
    void fallsBackToTheAdminThemeForUnmatchedAdminPrefixedPaths() {
        assertThat(resolver.resolve("/admin/anything")).isEqualTo(ThemeResolver.ADMIN);
    }

    @Test
    void fallsBackToTheFrontEndThemeForOtherUnmatchedPaths() {
        assertThat(resolver.resolve("/some/page")).isEqualTo(ThemeResolver.FRONT_END);
    }
}
