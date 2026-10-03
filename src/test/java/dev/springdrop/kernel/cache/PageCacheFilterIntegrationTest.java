package dev.springdrop.kernel.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.block.plugins.AccountGreeting;
import dev.springdrop.kernel.render.Attachments;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.LazyBuilder;
import dev.springdrop.kernel.render.Placeholder;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.kernel.render.Shell;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.PageRenderer;
import dev.springdrop.kernel.theme.StatusMessage;
import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

@SpringBootTest
class PageCacheFilterIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PageCacheFilter filter;

    @Autowired
    private PageCache pageCache;

    @Autowired
    private PageRenderer pages;

    @Autowired
    private RenderService renderer;

    @Autowired
    private AccountGreeting greeting;

    @BeforeEach
    void emptyCache() {
        pageCache.clear();
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
        pageCache.clear();
    }

    private static void signIn() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("edith", null,
                List.of()));
    }

    /** A request whose page draws the shell, answered with the status. */
    private MockHttpServletResponse serve(String method, Shell shell, int status, boolean startsSession)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/kept");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(HttpServletRequest served, HttpServletResponse answered)
                    throws java.io.IOException {
                if (startsSession) {
                    served.getSession(true);
                }
                served.setAttribute(PageRenderer.PAGE_ATTRIBUTE, new PageRenderer.Drawn(shell,
                        new RenderedPage(shell.html(), shell.cache(), Attachments.NONE)));
                answered.setStatus(status);
                answered.setContentType("text/html;charset=UTF-8");
                answered.getWriter().write(shell.html());
            }
        }));
        return response;
    }

    private static Shell shell(String html, Map<String, LazyBuilder> placeholders) {
        return new Shell(html, CacheMetadata.EMPTY, Attachments.NONE, placeholders, placeholders.size());
    }

    private String secondHeader(String method, Shell shell, int status, boolean startsSession) throws Exception {
        serve(method, shell, status, startsSession);
        return serve(method, shell, status, startsSession).getHeader(PageCacheFilter.HEADER);
    }

    @Test
    void aSignedInPageWithAClosurePlaceholderOrACsrfTokenIsNotKept() throws Exception {
        signIn();

        assertThat(secondHeader("GET", shell("<p>closure</p>", Map.of("placeholder-1",
                () -> Renderable.of("markup"))), 200, false)).isEqualTo("MISS");
        assertThat(secondHeader("GET", shell("<input name=\"_csrf\">", Map.of()), 200, false)).isEqualTo("MISS");
        assertThat(secondHeader("GET", shell("<p>named</p>", Map.of("placeholder-1",
                new Placeholder(AccountGreeting.ID, Map.of()))), 200, false)).isEqualTo("HIT");
    }

    @Test
    void anAnonymousPageThatStartsASessionOrHoldsACsrfTokenIsNotKept() throws Exception {
        assertThat(secondHeader("GET", shell("<p>session</p>", Map.of()), 200, true)).isEqualTo("MISS");
        assertThat(secondHeader("GET", shell("<input name=\"_csrf\">", Map.of()), 200, false)).isEqualTo("MISS");
        assertThat(secondHeader("HEAD", shell("<p>plain</p>", Map.of()), 200, false)).isEqualTo("HIT");
    }

    @Test
    void anAnswerOtherThanOkOrAWriteIsLeftAlone() throws Exception {
        assertThat(secondHeader("GET", shell("<p>missing</p>", Map.of()), 404, false)).isNull();
        assertThat(secondHeader("POST", shell("<p>posted</p>", Map.of()), 200, false)).isNull();
    }

    @Test
    void outsideARequestAPageOptingInIsDrawnAsAnyOther() {
        RequestContextHolder.resetRequestAttributes();

        assertThat(pages.render(PageChrome.of("SpringDrop", "Hours").withPageCache(), Renderable.of("markup")
                .with("value", "Open daily")).html()).contains("Open daily");
    }

    @Test
    void aPageShowingMessagesMayNotBeCached() {
        RenderedPage page = pages.render(PageChrome.of("SpringDrop", "Saved")
                .withMessage(StatusMessage.success("Saved.")), Renderable.of("markup").with("value", "Saved"));

        assertThat(page.cache().isCacheable()).isFalse();
    }

    @Test
    void aPlaceholderIsBuiltByItsBuilderOnly() {
        assertThatThrownBy(() -> new Placeholder(AccountGreeting.ID, Map.of()).build())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> renderer.render(Renderable.placeholder("missing", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The site has no placeholder builder missing.");
    }

    @Test
    void outsideARequestTheGreetingNamesTheAccountWithoutAToken() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(String.valueOf(greeting.build(Map.of()).data().get("value"))).contains("Log in");

        signIn();

        assertThat(String.valueOf(greeting.build(Map.of()).data().get("value"))).contains("Signed in as edith")
                .doesNotContain("_csrf");
    }
}
