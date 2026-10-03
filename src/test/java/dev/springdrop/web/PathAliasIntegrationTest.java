package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuNavigation;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.path.PathAlias;
import dev.springdrop.kernel.path.PathAliasFilterTestAccess;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.theme.Link;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class PathAliasIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "alias_page";

    private static final long EDITH = 111L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PathAliasManager aliases;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private RenderService renderer;

    @Autowired
    private MenuLinkContentService menuLinks;

    @Autowired
    private MenuNavigation navigation;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ViewDisplayManager displays;

    @BeforeEach
    void aPageType() {
        nodeTypes.save(NodeType.of(PAGE, "Alias page"));
        menuLinks.install();
    }

    @AfterEach
    void removeEverything() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(PAGE);
        aliases.all().forEach(alias -> aliases.deleteAll(alias.source()));
        menuLinks.inMenu(MenuConfig.MAIN).stream().filter(link -> link.title().equals("About"))
                .forEach(link -> MenuLinkContentService.entityId(link.id()).ifPresent(menuLinks::delete));
    }

    private static RequestPostProcessor editor() {
        return user(new AccountPrincipal(EDITH, "edith", "", true, List.of(NodePermissions.create(PAGE),
                NodePermissions.editOwn(PAGE), NodePermissions.ACCESS_CONTENT)));
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields) throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return mockMvc.perform(request.with(editor())).andReturn().getResponse();
    }

    private long create(String title, String alias) throws Exception {
        MockHttpServletResponse response = submit(NodeController.ADD_PATH + "/" + PAGE,
                Map.of("title", title, "path", alias));
        assertThat(response.getRedirectedUrl()).as(response.getContentAsString()).isNotNull();
        return Long.parseLong(response.getRedirectedUrl().substring("/node/".length()));
    }

    private MockHttpServletResponse fetch(String path, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(get(path).with(who)).andReturn().getResponse();
    }

    @Test
    void aNodeIsReachableAtItsAliasAndItsCanonicalPath() throws Exception {
        long id = create("About us", "/about-us");

        MockHttpServletResponse atAlias = fetch("/about-us", editor());
        MockHttpServletResponse atCanonical = fetch(NodeEntityType.path(id), editor());

        assertThat(atAlias.getStatus()).isEqualTo(200);
        assertThat(atCanonical.getStatus()).isEqualTo(200);
        assertThat(Jsoup.parse(atAlias.getContentAsString()).title())
                .isEqualTo(Jsoup.parse(atCanonical.getContentAsString()).title()).contains("About us");
        assertThat(aliases.aliasOf(NodeEntityType.path(id), EntityData.DEFAULT_LANGCODE)).contains("/about-us");
    }

    @Test
    void anAliasIsAnsweredWithTheAccessOfThePageItStandsFor() throws Exception {
        long id = create("Draft", "/draft");
        EntityData node = nodes.find(id).orElseThrow();
        Map<String, Object> values = new LinkedHashMap<>(node.fields());
        values.put(BaseFieldDefinition.STATUS, false);
        nodes.save(node.withFields(values), EDITH);

        assertThat(fetch("/draft", user("visitor").authorities(List.of(
                new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT)))).getStatus()).isEqualTo(403);
    }

    @Test
    void generatedLinksToThePageUseItsAlias() throws Exception {
        long id = create("About us", "/about");
        menuLinks.save(MenuLink.of(null, MenuConfig.MAIN, "About", NodeEntityType.path(id)));

        String teaser = renderer.render(nodes.build(nodes.find(id).orElseThrow(), ViewDisplayConfig.TEASER_MODE,
                false)).html();
        List<Link> menu = navigation.primary("/");

        assertThat(Jsoup.parseBodyFragment(teaser).selectFirst("h2 a").attr("href")).isEqualTo("/about");
        assertThat(menu).extracting(Link::url).contains("/about");
        assertThat(aliases.outbound("/node/999999")).isEqualTo("/node/999999");
    }

    @Test
    void aReferenceToThePageLinksToItsAlias() throws Exception {
        long target = create("About us", "/about");
        fields.createStorage(new FieldStorageConfig("field_related", NodeEntityType.ID, EntityReferenceFieldType.ID,
                1, Map.of(EntityReferenceFieldType.TARGET_TYPE, NodeEntityType.ID)));
        fields.createInstance(FieldInstanceConfig.of("field_related", NodeEntityType.ID, PAGE, "Related"));
        try {
            String drawn = displays.render(NodeEntityType.ID, PAGE, ViewDisplayConfig.DEFAULT_MODE,
                    Map.of("field_related", target));

            assertThat(Jsoup.parseBodyFragment(drawn).selectFirst("a").attr("href")).isEqualTo("/about");
        } finally {
            fields.deleteStorage(NodeEntityType.ID, "field_related");
        }
    }

    @Test
    void anAliasWrittenWronglyServedByTheSiteOrTakenIsRefusedOnTheForm() throws Exception {
        create("About us", "/about");
        for (Map.Entry<String, String> attempt : Map.of(
                "/news/", PathAliasManager.PATTERN_MESSAGE,
                "about", PathAliasManager.PATTERN_MESSAGE,
                "/user/login", "The path /user/login is already a page of the site.",
                "/about", "The alias /about is already in use.").entrySet()) {
            MockHttpServletResponse response = submit(NodeController.ADD_PATH + "/" + PAGE,
                    Map.of("title", "Other", "path", attempt.getKey()));

            Document form = Jsoup.parse(response.getContentAsString());
            assertThat(form.text()).contains(attempt.getValue());
            assertThat(form.selectFirst("input[name=path]").val()).isEqualTo(attempt.getKey());
        }
        assertThat(queries.query(NodeEntityType.ID).count()).isOne();
    }

    @Test
    void editingKeepsTheAliasAndABlankAliasTakesItAway() throws Exception {
        long id = create("About us", "/about");
        assertThat(Jsoup.parse(fetch(NodeEntityType.editPath(id), editor()).getContentAsString())
                .selectFirst("input[name=path]").val()).isEqualTo("/about");

        submit(NodeEntityType.editPath(id), Map.of("title", "About us", "path", "/about"));
        assertThat(aliases.aliasOf(NodeEntityType.path(id), EntityData.DEFAULT_LANGCODE)).contains("/about");
        submit(NodeEntityType.editPath(id), Map.of("title", "About us", "path", "/about/team"));
        assertThat(aliases.sourceOf("/about", EntityData.DEFAULT_LANGCODE)).isEmpty();
        submit(NodeEntityType.editPath(id), Map.of("title", "About us", "path", ""));

        assertThat(aliases.aliasOf(NodeEntityType.path(id), EntityData.DEFAULT_LANGCODE)).isEmpty();
        assertThat(fetch("/about/team", editor()).getHeader("Location")).isEqualTo(NodeEntityType.path(id));
    }

    @Test
    void deletingTheNodeTakesItsAliasesAway() throws Exception {
        long id = create("About us", "/about");

        nodes.delete(id);

        assertThat(aliases.all()).isEmpty();
    }

    @Test
    void theManagerRefusesWhatTheFormRefuses() {
        aliases.save("/node/1", "/welcome", EntityData.DEFAULT_LANGCODE);
        aliases.save("/node/1", "/welcome", "fr");

        assertThat(aliases.all()).extracting(PathAlias::langcode).containsExactly("en", "fr");
        assertThat(aliases.refusal("/node/1", "/welcome", "en")).isEmpty();
        assertThat(aliases.refusal("/node/2", "/" + "a".repeat(255), "en")).contains(PathAliasManager.PATTERN_MESSAGE);
        assertThat(aliases.refusal("/node/2", "/js/editor.js", "en")).isPresent();
        assertThat(aliases.refusal("/node/2", "/node/9999", "en"))
                .contains("The path /node/9999 is already a page of the site.");
        assertThat(aliases.refusal("/node/2", "/media-library", "en"))
                .contains("The path /media-library is already a page of the site.");
        assertThatThrownBy(() -> aliases.save("/node/2", "/welcome", "en"))
                .isInstanceOf(IllegalArgumentException.class);
        aliases.delete("/node/1", "fr");
        assertThat(aliases.sourceOf("/welcome", "fr")).isEmpty();
        aliases.save("/node/1", "  ", "en");
        assertThat(aliases.all()).isEmpty();
    }

    @Test
    void theRequestForAnAliasCarriesTheSourcesPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/about");
        request.setServerName("example.org");

        PathAliasFilterTestAccess rewritten = new PathAliasFilterTestAccess(request, "/node/7");

        assertThat(rewritten.getRequestURI()).isEqualTo("/node/7");
        assertThat(rewritten.getRequestURL().toString()).isEqualTo("http://example.org/node/7");
        assertThat(rewritten.getServletPath()).isEqualTo("/node/7");
        assertThat(rewritten.getPathInfo()).isNull();
    }
}
