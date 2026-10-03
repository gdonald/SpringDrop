package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.render.Placeholder;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
class BigPipeIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BigPipe bigPipe;

    @Autowired
    private BigPipeFilter filter;

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private static MockHttpServletRequest signedIn(String method) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("edith", null,
                List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/kept");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void onlyAReadBySomeoneSignedInIsStreamed() {
        assertThat(bigPipe.active()).isFalse();
        signedIn("POST");
        assertThat(bigPipe.active()).isFalse();
        signedIn("GET");
        assertThat(bigPipe.active()).isTrue();
        RequestContextHolder.resetRequestAttributes();
        assertThat(bigPipe.active()).isFalse();
    }

    @Test
    void aPageWithoutABodyOrWithNothingPendingIsSentAsItIs() throws Exception {
        MockHttpServletRequest request = signedIn("GET");
        MockHttpServletResponse plain = new MockHttpServletResponse();
        filter.doFilter(request, plain, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest served,
                    jakarta.servlet.http.HttpServletResponse answered) throws java.io.IOException {
                answered.setCharacterEncoding("UTF-8");
                answered.getWriter().write("<p>no pending</p>");
            }
        }));

        MockHttpServletRequest pendingRequest = signedIn("GET");
        pendingRequest.setAttribute(BigPipe.PENDING_ATTRIBUTE, new BigPipe.Pending(Map.of("placeholder-1",
                new Placeholder("account_greeting", Map.of())), 1));
        MockHttpServletResponse bodiless = new MockHttpServletResponse();
        filter.doFilter(pendingRequest, bodiless, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest served,
                    jakarta.servlet.http.HttpServletResponse answered) throws java.io.IOException {
                answered.setCharacterEncoding("UTF-8");
                answered.getWriter().write("<p>no body</p>");
            }
        }));

        assertThat(plain.getContentAsString()).isEqualTo("<p>no pending</p>");
        assertThat(bodiless.getContentAsString()).isEqualTo("<p>no body</p>");
    }

    @Test
    void aReplacementKeepsItsHtmlInsideAJsonString() {
        signedIn("GET");

        String script = bigPipe.replacement("placeholder-1", () -> Renderable.of("markup")
                .with("value", "<b>\"Tom\" & \\ Jerry\u0001</b>").styleSheet("/css/greeting.css"), 1);

        assertThat(script).startsWith("<script type=\"application/json\" " + BigPipe.REPLACEMENT_ATTRIBUTE
                + "=\"placeholder-1\">").doesNotContain("<b>").contains("\\u003cb\\u003e")
                .contains("\\\"Tom\\\" \\u0026 \\\\ Jerry\\u0001").contains("greeting.css");
    }
}
