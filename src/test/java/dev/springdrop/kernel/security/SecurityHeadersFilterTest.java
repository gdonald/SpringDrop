package dev.springdrop.kernel.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityHeadersFilterTest {

    @Test
    void aHeaderConfiguredBlankIsLeftOff() throws Exception {
        SecurityHeadersFilter filter = new SecurityHeadersFilter(
                new SecurityHeadersProperties("", "", "", ""));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeaderNames()).isEmpty();
        assertThat(request.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE)).isNotNull();
    }

    @Test
    void thePolicyTemplateTakesTheNonceOfTheResponse() {
        SecurityHeadersProperties properties = new SecurityHeadersProperties(
                "DENY", "nosniff", "no-referrer", "script-src 'nonce-{nonce}'");

        assertThat(properties.policyWith("abc123")).isEqualTo("script-src 'nonce-abc123'");
    }
}
