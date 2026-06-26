package dev.springdrop.kernel.maintenance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

class MaintenanceModeFilterTest {

    private final ITemplateEngine templateEngine = Mockito.mock(ITemplateEngine.class);

    private MaintenanceModeFilter filterWith(boolean enabled, MaintenanceBypass bypass) {
        return new MaintenanceModeFilter(
                new MaintenanceProperties(enabled, "Down for maintenance"),
                bypass,
                templateEngine);
    }

    private final MaintenanceBypass denyAll = request -> false;
    private final MaintenanceBypass allowAll = request -> true;

    @Test
    void passesThroughWhenDisabled() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWith(false, denyAll).doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    void passesThroughExcludedPaths() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWith(true, denyAll).doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void passesThroughWhenBypassAllows() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWith(true, allowAll).doFilterInternal(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void servesMaintenancePageWhenBlocked() throws Exception {
        when(templateEngine.process(eq("maintenance"), any(IContext.class)))
                .thenReturn("<html>maintenance</html>");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterWith(true, denyAll).doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertThat(response.getContentType()).isEqualTo("text/html;charset=UTF-8");
        assertThat(response.getContentAsString()).contains("maintenance");
        assertThat(chain.getRequest()).as("blocked request does not continue").isNull();
    }

    @Test
    void defaultBypassAllowsNoOne() {
        assertThat(new DefaultMaintenanceBypass().isAllowed(new MockHttpServletRequest())).isFalse();
    }
}
