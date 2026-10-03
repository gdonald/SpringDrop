package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.block.plugins.AccountBlock;
import dev.springdrop.kernel.cache.PageCache;
import dev.springdrop.kernel.cache.PageCacheFilter;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.BigPipe;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class PageCacheIntegrationTest extends AbstractIntegrationTest {

    private static final String STORY = "page_cache_story";

    private static final String PLACEMENT = "page_cache_account";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageCache pageCache;

    @Autowired
    private BlockPlacementManager placements;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private MenuLinkContentService menuLinks;

    @Autowired
    private UserAccountService accounts;

    @BeforeEach
    void anAccountBlockAndAStory() {
        menuLinks.install();
        accounts.install();
        pageCache.clear();
        placements.save(BlockPlacement.of(PLACEMENT, Theme.FRONT_END, "sidebar", AccountBlock.ID, "Account"));
        nodeTypes.save(NodeType.of(STORY, "Story"));
        story("Opening hours");
    }

    @AfterEach
    void removeEverything() {
        placements.delete(PLACEMENT);
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(STORY);
        configStore.delete(SiteInformation.CONFIG_NAME);
        pageCache.clear();
    }

    private void story(String title) {
        nodes.save(EntityData.of(NodeEntityType.ID, null, STORY, title, Map.of("status", true, "promote", true)),
                1L);
    }

    private MockHttpServletResponse read(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static RequestPostProcessor account(long id, String name, String role) {
        return user(new AccountPrincipal(id, name, "", true, List.of(NodePermissions.ACCESS_CONTENT),
                List.of(role)));
    }

    private static String cacheHeader(MockHttpServletResponse response) {
        return response.getHeader(PageCacheFilter.HEADER);
    }

    @Test
    void anAnonymousPageIsServedFromTheCacheUntilWhatItShowsChanges() throws Exception {
        MockHttpServletResponse first = read(get("/"));
        MockHttpServletResponse second = read(get("/"));

        assertThat(cacheHeader(first)).isEqualTo("MISS");
        assertThat(cacheHeader(second)).isEqualTo("HIT");
        assertThat(second.getContentAsString()).isEqualTo(first.getContentAsString()).contains("Log in");
        assertThat(second.getContentType()).isEqualTo(first.getContentType());

        story("Closing hours");
        assertThat(cacheHeader(read(get("/")))).isEqualTo("MISS");

        SiteInformation site = SiteInformation.DEFAULTS;
        configStore.save(SiteInformation.CONFIG_NAME, new SiteInformation("Branch library", site.slogan(), site.mail(),
                site.url(), site.frontPage()));
        assertThat(cacheHeader(read(get("/")))).isEqualTo("MISS");
    }

    @Test
    void eachAddressIsKeptOnItsOwn() throws Exception {
        read(get("/"));

        assertThat(cacheHeader(read(get("/").param("page", "2")))).isEqualTo("MISS");
        assertThat(cacheHeader(read(get("/").param("page", "2")))).isEqualTo("HIT");
    }

    @Test
    void aKeptPageCarriesTheNonceMintedForTheRequestItAnswers() throws Exception {
        read(get("/"));

        MockHttpServletResponse kept = read(get("/"));

        String policy = kept.getHeader("Content-Security-Policy");
        assertThat(cacheHeader(kept)).isEqualTo("HIT");
        Jsoup.parse(kept.getContentAsString()).select("script[nonce]").forEach(script ->
                assertThat(policy).contains("'nonce-" + script.attr("nonce") + "'"));
    }

    @Test
    void someoneWithASessionIsNotAnsweredFromTheAnonymousCache() throws Exception {
        read(get("/"));

        assertThat(cacheHeader(read(get("/").session(new MockHttpSession())))).isNull();
    }

    @Test
    void aSignedInPageVariesByAccountAndBuildsItsGreetingForEachRequest() throws Exception {
        MockHttpServletResponse editorFirst = read(inline(get("/").with(account(41L, "edith", "editor"))));
        MockHttpServletResponse editorAgain = read(inline(get("/").with(account(41L, "edith", "editor"))));
        MockHttpServletResponse writer = read(inline(get("/").with(account(42L, "frank", "writer"))));

        assertThat(List.of(cacheHeader(editorFirst), cacheHeader(editorAgain), cacheHeader(writer)))
                .containsExactly("MISS", "HIT", "MISS");
        assertThat(editorAgain.getContentAsString()).contains("Signed in as edith");
        assertThat(writer.getContentAsString()).contains("Signed in as frank").doesNotContain("edith");
        assertThat(token(editorAgain)).isNotEmpty().isNotEqualTo(token(editorFirst));
    }

    /** Without JavaScript, so the greeting is drawn in the page rather than streamed after it. */
    private static MockHttpServletRequestBuilder inline(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie(BigPipe.NO_JS_COOKIE, "1"));
    }

    private static String token(MockHttpServletResponse response) throws Exception {
        return Jsoup.parse(response.getContentAsString()).select("form.account-greeting input[name=_csrf]").val();
    }

    @Test
    void pagesThatDoNotOptInAreNeverKept() throws Exception {
        assertThat(cacheHeader(read(get("/user/login")))).isNull();
        assertThat(cacheHeader(read(get("/no/such/page")))).isNull();
    }
}
