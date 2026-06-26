package dev.springdrop.kernel.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenNoneIsSupplied() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesAnIdWhenTheSuppliedOneIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    void reusesAnInboundCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("abc-123");
    }
}
