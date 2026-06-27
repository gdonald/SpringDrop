package dev.springdrop.kernel.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteRegistryTest {

    private final RouteRegistry registry = new RouteRegistry(List.of(
            () -> List.of(
                    RouteDefinition.frontEnd("/articles", "articles", "Articles"),
                    RouteDefinition.admin("/admin/content/**", "content", "Content", "access content overview"))));

    @Test
    void matchesAnExactPath() {
        assertThat(registry.match("/articles")).get()
                .extracting(RouteDefinition::admin).isEqualTo(false);
    }

    @Test
    void matchesAnAntPattern() {
        assertThat(registry.match("/admin/content/node/1")).get()
                .extracting(RouteDefinition::requiredPermission)
                .isEqualTo("access content overview");
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        assertThat(registry.match("/nothing")).isEmpty();
    }

    @Test
    void exposesAllRegisteredRoutes() {
        assertThat(registry.all()).hasSize(2);
    }
}
