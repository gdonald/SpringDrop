package dev.springdrop.kernel.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.support.AbstractIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Runs against a real servlet container so the container's error dispatch, and
 * with it the site-configured error pages, are exercised end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SiteErrorPageIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void configureSitePages() {
        configStore.save(ErrorPagesConfig.CONFIG_NAME, new ErrorPagesConfig("/site-forbidden", "/site-not-found"));
    }

    @AfterEach
    void restoreDefaults() {
        configStore.save(ErrorPagesConfig.CONFIG_NAME, ErrorPagesConfig.DEFAULTS);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", "text/html")
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void anUnmatchedPathServesTheConfiguredNotFoundPage() throws Exception {
        HttpResponse<String> response = get("/no-such-page");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("site not found page");
    }

    @Test
    void aDeniedPathServesTheConfiguredForbiddenPage() throws Exception {
        HttpResponse<String> response = get("/needs-permission");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).isEqualTo("site forbidden page");
    }

    @TestConfiguration
    static class SitePages {

        @Bean
        RouteRegistrar sitePageRoutes() {
            return () -> List.of(
                    RouteDefinition.frontEnd("/site-not-found", "site_404", "Not found"),
                    RouteDefinition.frontEnd("/site-forbidden", "site_403", "Access denied"),
                    RouteDefinition.admin("/needs-permission", "restricted", "Restricted", "administer things"));
        }

        @Controller
        static class SitePageController {

            @GetMapping("/site-not-found")
            @ResponseBody
            String notFound() {
                return "site not found page";
            }

            @GetMapping("/site-forbidden")
            @ResponseBody
            String forbidden() {
                return "site forbidden page";
            }

            @GetMapping("/needs-permission")
            @ResponseBody
            String restricted() {
                return "restricted";
            }
        }
    }
}
