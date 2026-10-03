package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.block.plugins.AccountBlock;
import dev.springdrop.kernel.cache.PageCache;
import dev.springdrop.kernel.cache.PageCacheFilter;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.theme.BigPipe;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class BigPipeIntegrationTest extends AbstractIntegrationTest {

    private static final String PLACEMENT = "big_pipe_account";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockPlacementManager placements;

    @Autowired
    private PageCache pageCache;

    @Autowired
    private MenuLinkContentService menuLinks;

    @Autowired
    private UserAccountService accounts;

    @BeforeEach
    void anAccountBlock() {
        menuLinks.install();
        accounts.install();
        pageCache.clear();
        placements.save(BlockPlacement.of(PLACEMENT, Theme.FRONT_END, "sidebar", AccountBlock.ID, "Account"));
    }

    @AfterEach
    void removeTheBlock() {
        placements.delete(PLACEMENT);
        pageCache.clear();
    }

    private static RequestPostProcessor edith() {
        return user(new AccountPrincipal(41L, "edith", "", true, List.of(NodePermissions.ACCESS_CONTENT),
                List.of("editor")));
    }

    private MockHttpServletResponse read(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void aSignedInPageStreamsItsShellThenItsPlaceholders() throws Exception {
        MockHttpServletResponse streamed = read(get("/").with(edith()));
        String html = streamed.getContentAsString();

        int marker = html.indexOf(BigPipe.PLACEHOLDER_ATTRIBUTE + "=\"placeholder-");
        int replacement = html.indexOf(BigPipe.REPLACEMENT_ATTRIBUTE + "=\"placeholder-");
        assertThat(marker).isPositive();
        assertThat(replacement).isGreaterThan(html.indexOf("</main>")).isLessThan(html.lastIndexOf("</body>"));
        assertThat(html.substring(replacement)).contains("Signed in as edith");
        assertThat(html.substring(0, replacement)).doesNotContain("Signed in as edith");
        assertThat(html).contains("initBigPipe").contains(BigPipe.NO_JS_PATH + "?destination=%2F");
        assertThat(streamed.isCommitted()).isTrue();
    }

    @Test
    void aBrowserWithoutJavaScriptIsSentBackToThePageItAskedFor() throws Exception {
        String html = read(get("/?page=1").with(edith())).getContentAsString();

        assertThat(html).contains(BigPipe.NO_JS_PATH + "?destination=%2F%3Fpage%3D1");
    }

    @Test
    void aPageFromTheDynamicPageCacheIsStreamedAsWell() throws Exception {
        read(get("/").with(edith()));

        MockHttpServletResponse kept = read(get("/").with(edith()));

        assertThat(kept.getHeader(PageCacheFilter.HEADER)).isEqualTo("HIT");
        assertThat(kept.getContentAsString()).contains(BigPipe.REPLACEMENT_ATTRIBUTE);
    }

    @Test
    void withJavaScriptOffThePageIsDrawnWhole() throws Exception {
        String html = read(get("/").with(edith()).cookie(new Cookie(BigPipe.NO_JS_COOKIE, "1")))
                .getContentAsString();

        assertThat(html).contains("Signed in as edith").doesNotContain(BigPipe.PLACEHOLDER_ATTRIBUTE)
                .doesNotContain(BigPipe.REPLACEMENT_ATTRIBUTE);
    }

    @Test
    void someoneNotSignedInGetsThePageWhole() throws Exception {
        assertThat(read(get("/")).getContentAsString()).contains("Log in")
                .doesNotContain(BigPipe.PLACEHOLDER_ATTRIBUTE);
    }

    @Test
    void theNoJavaScriptAddressSetsTheCookieAndSendsTheReaderBack() throws Exception {
        MockHttpServletResponse back = read(get(BigPipe.NO_JS_PATH).param("destination", "/node").with(edith()));
        MockHttpServletResponse elsewhere = read(get(BigPipe.NO_JS_PATH)
                .param("destination", "https://example.com/").with(edith()));

        assertThat(back.getRedirectedUrl()).isEqualTo("/node");
        assertThat(back.getCookie(BigPipe.NO_JS_COOKIE).getValue()).isEqualTo("1");
        assertThat(elsewhere.getRedirectedUrl()).isEqualTo("/");
    }
}
