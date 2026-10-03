package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.path.Redirect;
import dev.springdrop.kernel.path.RedirectManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class RedirectIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "redirect_page";

    private static final long EDITH = 131L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedirectManager redirects;

    @Autowired
    private PathAliasManager aliases;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private MenuLinkContentService menuLinks;

    @BeforeEach
    void aPageType() {
        nodeTypes.save(NodeType.of(PAGE, "Redirect page"));
        menuLinks.install();
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(PAGE);
        aliases.all().forEach(alias -> aliases.deleteAll(alias.source()));
        redirects.all().forEach(redirect -> redirects.delete(redirect.id()));
    }

    private static RequestPostProcessor editor() {
        return user(new AccountPrincipal(EDITH, "edith", "", true, List.of(NodePermissions.create(PAGE),
                NodePermissions.editOwn(PAGE), NodePermissions.ACCESS_CONTENT)));
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(RedirectController.ADMINISTER_REDIRECTS));
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields, RequestPostProcessor who)
            throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return mockMvc.perform(request.with(who)).andReturn().getResponse();
    }

    private long create(String alias) throws Exception {
        return Long.parseLong(submit(NodeController.ADD_PATH + "/" + PAGE, Map.of("title", "About us", "path", alias),
                editor()).getRedirectedUrl().substring("/node/".length()));
    }

    private void rename(long id, String alias) throws Exception {
        submit(NodeEntityType.editPath(id), Map.of("title", "About us", "path", alias), editor());
    }

    private MockHttpServletResponse fetch(String path) throws Exception {
        return mockMvc.perform(get(path).with(editor())).andReturn().getResponse();
    }

    @Test
    void changingAnAliasSendsTheOldPathToTheNewOnePermanently() throws Exception {
        long id = create("/about");

        rename(id, "/about-us");

        MockHttpServletResponse old = fetch("/about");
        assertThat(old.getStatus()).isEqualTo(301);
        assertThat(old.getHeader("Location")).isEqualTo("/about-us");
        assertThat(fetch("/about-us").getStatus()).isEqualTo(200);
    }

    @Test
    void anAliasChangedTwiceSendsEveryOldPathStraightToTheNewest() throws Exception {
        long id = create("/about");
        rename(id, "/about-us");
        rename(id, "/team");

        assertThat(fetch("/about").getHeader("Location")).isEqualTo("/team");
        assertThat(fetch("/about-us").getHeader("Location")).isEqualTo("/team");
    }

    @Test
    void anAliasTakenAwaySendsItsPathToThePageAndOneTakenBackAnswersAgain() throws Exception {
        long id = create("/about");

        rename(id, "");
        assertThat(fetch("/about").getHeader("Location")).isEqualTo(NodeEntityType.path(id));
        rename(id, "/about");

        assertThat(fetch("/about").getStatus()).isEqualTo(200);
        assertThat(redirects.find("/about")).isEmpty();
    }

    @Test
    void theQueryStringIsCarriedToADestinationWithoutOne() throws Exception {
        redirects.save(null, "/old", "/new", 302);
        redirects.save(null, "/search-old", "https://example.com/find?q=all", 307);

        assertThat(mockMvc.perform(get("/old?page=2")).andReturn().getResponse().getHeader("Location"))
                .isEqualTo("/new?page=2");
        MockHttpServletResponse external = mockMvc.perform(get("/search-old?page=2")).andReturn().getResponse();
        assertThat(external.getStatus()).isEqualTo(307);
        assertThat(external.getHeader("Location")).isEqualTo("https://example.com/find?q=all");
        assertThat(mockMvc.perform(get("/app/old").contextPath("/app")).andReturn().getResponse()
                .getHeader("Location")).isEqualTo("/app/new");
    }

    @Test
    void theManagerRefusesRedirectsThatCannotWork() {
        redirects.save(null, "/old", "/new", 301);
        Redirect saved = redirects.find("/old").orElseThrow();

        assertThat(redirects.refusal(null, "old", "/new", 301)).contains(PathAliasManager.PATTERN_MESSAGE);
        assertThat(redirects.refusal(null, "/" + "a".repeat(255), "/new", 301))
                .contains(PathAliasManager.PATTERN_MESSAGE);
        assertThat(redirects.refusal(null, "/x", "javascript:alert(1)", 301)).contains(RedirectManager.DESTINATION_MESSAGE);
        assertThat(redirects.refusal(null, "/x", "/" + "a".repeat(2048), 301))
                .contains(RedirectManager.DESTINATION_MESSAGE);
        assertThat(redirects.refusal(null, "/x", "/x", 301)).contains(RedirectManager.SELF_MESSAGE);
        assertThat(redirects.refusal(null, "/x", "/y", 300)).contains(RedirectManager.STATUS_MESSAGE);
        assertThat(redirects.refusal(null, "/old", "/y", 301)).contains("The path /old already redirects.");
        assertThat(redirects.refusal(saved.id(), "/old", "/y", 301)).isEmpty();
        assertThatThrownBy(() -> redirects.save(null, "/old", "/y", 301)).isInstanceOf(IllegalArgumentException.class);
        assertThat(redirects.find(999_999L)).isEmpty();
    }

    @Test
    void redirectsAreListedAddedEditedAndDeleted() throws Exception {
        assertThat(Jsoup.parse(mockMvc.perform(get(RedirectController.PATH + "/add").with(administrator()))
                .andReturn().getResponse().getContentAsString()).select("select[name=status] option"))
                .extracting(option -> option.text()).containsExactly("301 Moved permanently", "302 Found",
                        "303 See other", "307 Temporary redirect", "308 Permanent redirect");

        submit(RedirectController.PATH + "/add", Map.of("source", "/old-page", "destination", "/about",
                "status", "301"), administrator());
        Redirect saved = redirects.find("/old-page").orElseThrow();
        assertThat(Jsoup.parse(mockMvc.perform(get(RedirectController.PATH).with(administrator())).andReturn()
                .getResponse().getContentAsString()).selectFirst("tr[data-redirect=" + saved.id() + "]").text())
                .contains("/old-page").contains("/about").contains("301");

        assertThat(mockMvc.perform(get(RedirectController.PATH + "/manage/" + saved.id()).with(administrator()))
                .andReturn().getResponse().getContentAsString()).contains("value=\"/old-page\"");
        submit(RedirectController.PATH + "/manage/" + saved.id(), Map.of("source", "/old-page",
                "destination", "https://example.com/", "status", "308"), administrator());
        assertThat(redirects.find("/old-page")).map(Redirect::status).contains(308);

        assertThat(mockMvc.perform(get(RedirectController.PATH + "/manage/" + saved.id() + "/delete")
                .with(administrator())).andReturn().getResponse().getContentAsString())
                .contains("Delete the redirect from /old-page?");
        submit(RedirectController.PATH + "/manage/" + saved.id() + "/delete", Map.of(), administrator());
        assertThat(redirects.all()).isEmpty();
    }

    @Test
    void aRedirectFormBreakingTheRulesComesBackWithTheReasonOnItsField() throws Exception {
        redirects.save(null, "/taken", "/about", 301);

        String wrong = submit(RedirectController.PATH + "/add", Map.of("source", "old page", "destination",
                "ftp://x", "status", "301"), administrator()).getContentAsString();
        String self = submit(RedirectController.PATH + "/add", Map.of("source", "/same", "destination", "/same",
                "status", "301"), administrator()).getContentAsString();
        String taken = submit(RedirectController.PATH + "/add", Map.of("source", "/taken", "destination", "/x",
                "status", "301"), administrator()).getContentAsString();
        String status = submit(RedirectController.PATH + "/add", Map.of("source", "/fresh", "destination", "/x",
                "status", "999"), administrator()).getContentAsString();
        String noStatus = submit(RedirectController.PATH + "/add", Map.of("source", "/fresh", "destination", "/x",
                "status", "permanent"), administrator()).getContentAsString();

        assertThat(Jsoup.parse(wrong).text()).contains(PathAliasManager.PATTERN_MESSAGE)
                .contains(RedirectManager.DESTINATION_MESSAGE);
        assertThat(Jsoup.parse(self).selectFirst("#destination").parent().text()).contains(RedirectManager.SELF_MESSAGE);
        assertThat(Jsoup.parse(taken).text()).contains("The path /taken already redirects.");
        assertThat(Jsoup.parse(status).selectFirst("#status").parent().text()).contains(RedirectManager.STATUS_MESSAGE);
        assertThat(Jsoup.parse(noStatus).text()).contains(RedirectManager.STATUS_MESSAGE);
        assertThat(redirects.all()).hasSize(1);
    }

    @Test
    void aRedirectTheSiteLacksIsNotFoundAndThePagesNeedThePermission() throws Exception {
        assertThat(mockMvc.perform(get(RedirectController.PATH + "/manage/999999").with(administrator()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(get(RedirectController.PATH).with(editor())).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
        assertThat(Jsoup.parse(mockMvc.perform(get(RedirectController.PATH).with(administrator())).andReturn()
                .getResponse().getContentAsString()).text()).contains("There are no redirects yet.");
    }

    @Test
    void aSavedAliasRemovesARedirectFromItsPathEvenWhenUnchanged() {
        aliases.save("/node/1", "/welcome", EntityData.DEFAULT_LANGCODE);
        redirects.save(null, "/welcome", "/elsewhere", 302);

        aliases.save("/node/1", "/welcome", EntityData.DEFAULT_LANGCODE);

        assertThat(redirects.find("/welcome")).isEmpty();
    }
}
