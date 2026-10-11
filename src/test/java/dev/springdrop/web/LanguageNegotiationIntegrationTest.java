package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.language.LanguageNegotiator;
import dev.springdrop.kernel.language.LanguageSettings;
import dev.springdrop.kernel.language.NegotiationSettings;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class LanguageNegotiationIntegrationTest extends AbstractIntegrationTest {

    private static final String PAGE = "page";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private LanguageNegotiator negotiator;

    @Autowired
    private ConfigStore config;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private PathAliasManager aliases;

    private long node;

    @BeforeEach
    void threeLanguagesAndAPage() {
        accounts.install();
        clear();
        languages.save(new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr", "fr.example.com"));
        languages.save(new LanguageConfig("de", "German", LanguageConfig.LTR, 2, "de", ""));
        languages.save(new LanguageConfig("ar", "Arabic", LanguageConfig.RTL, 3, "ar", ""));
        types.save(NodeType.of(PAGE, "Basic page"));
        node = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, PAGE, "About us",
                Map.of(BaseFieldDefinition.STATUS, true, BaseFieldDefinition.OWNER, 1L)), 1L).id()).longValue();
        aliases.save(NodeEntityType.path(node), "/about", EntityData.DEFAULT_LANGCODE);
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        List.of("fr", "de", "ar").forEach(languages::delete);
        config.delete(NegotiationSettings.CONFIG_NAME);
        config.delete(LanguageSettings.CONFIG_NAME);
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
        accounts.findByName("ada").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
    }

    private static RequestPostProcessor reader() {
        return user("reader").authorities(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
    }

    private RequestPostProcessor ada(String preferred) {
        long id = accounts.create("ada", "ada@example.com", "", Map.of(UserEntityType.PREFERRED_LANGUAGE, preferred))
                .id();
        return user(new AccountPrincipal(id, "ada", "", true, List.of(NodePermissions.ACCESS_CONTENT)));
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request).andReturn().getResponse().getContentAsString());
    }

    private static String lang(Document page) {
        return page.selectFirst("html").attr("lang");
    }

    @Test
    void aUrlPrefixSelectsTheLanguageThePageIsShownIn() throws Exception {
        assertThat(lang(page(get(NodeEntityType.path(node)).with(reader())))).isEqualTo("en");
        assertThat(lang(page(get("/fr" + NodeEntityType.path(node)).with(reader())))).isEqualTo("fr");
        assertThat(lang(page(get("/de/about").with(reader())))).isEqualTo("de");
        assertThat(page(get("/de/about").with(reader())).text()).contains("About us");
    }

    @Test
    void thePrefixAloneIsTheFrontPageInThatLanguage() throws Exception {
        assertThat(mockMvc.perform(get("/fr").with(reader())).andReturn().getResponse().getStatus())
                .isEqualTo(mockMvc.perform(get("/").with(reader())).andReturn().getResponse().getStatus());
    }

    @Test
    void withoutAPrefixTheAccountsPreferredLanguageIsUsed() throws Exception {
        RequestPostProcessor ada = ada("de");

        assertThat(lang(page(get(NodeEntityType.path(node)).with(ada)))).isEqualTo("de");
        assertThat(lang(page(get("/fr" + NodeEntityType.path(node)).with(ada)))).isEqualTo("fr");
    }

    @Test
    void aPreferredLanguageTheSiteDoesNotHaveFallsBackToTheDefault() throws Exception {
        assertThat(lang(page(get(NodeEntityType.path(node)).with(ada("pt"))))).isEqualTo("en");
    }

    @Test
    void theDefaultLanguageIsTheLastResort() throws Exception {
        languages.makeDefault("de");

        assertThat(lang(page(get(NodeEntityType.path(node)).with(reader())))).isEqualTo("de");
    }

    @Test
    void aRightToLeftLanguageSetsThePagesDirection() throws Exception {
        Document arabic = page(get("/ar" + NodeEntityType.path(node)).with(reader()));

        assertThat(arabic.selectFirst("html").attr("dir")).isEqualTo("rtl");
        assertThat(page(get(NodeEntityType.path(node)).with(reader())).selectFirst("html").attr("dir"))
                .isEqualTo("ltr");
    }

    @Test
    void accessIsCheckedOnThePathWithoutItsPrefix() throws Exception {
        assertThat(mockMvc.perform(get("/fr" + LanguageController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get("/fr" + LanguageController.PATH).with(user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_LANGUAGES)))).andReturn().getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void theSessionMethodKeepsTheLanguageAParameterChose() throws Exception {
        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.SESSION), NegotiationSettings.PREFIX));
        MockHttpSession session = new MockHttpSession();

        assertThat(lang(page(get(NodeEntityType.path(node)).param("language", "de").session(session)
                .with(reader())))).isEqualTo("de");
        assertThat(lang(page(get(NodeEntityType.path(node)).session(session).with(reader())))).isEqualTo("de");
        assertThat(lang(page(get(NodeEntityType.path(node)).param("language", "pt").session(session)
                .with(reader())))).isEqualTo("de");
        assertThat(lang(page(get(NodeEntityType.path(node)).with(reader())))).isEqualTo("en");
    }

    @Test
    void theDomainMethodReadsTheHostAskedFor() throws Exception {
        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.URL), NegotiationSettings.DOMAIN));

        assertThat(lang(page(get(NodeEntityType.path(node)).with(reader()).with(request -> {
            request.setServerName("fr.example.com");
            return request;
        })))).isEqualTo("fr");
        assertThat(lang(page(get(NodeEntityType.path(node)).with(reader())))).isEqualTo("en");
    }

    @Test
    void withTheUrlMethodOffAPrefixIsAnOrdinaryPath() throws Exception {
        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.USER), NegotiationSettings.PREFIX));

        MockHttpServletResponse prefixed = mockMvc.perform(get("/fr" + NodeEntityType.path(node)).with(reader()))
                .andReturn().getResponse();

        assertThat(prefixed.getStatus()).isEqualTo(404);
    }

    @Test
    void anUnknownMethodNameChoosesNothing() throws Exception {
        negotiator.save(new NegotiationSettings(List.of("browser", NegotiationSettings.URL),
                NegotiationSettings.PREFIX));

        assertThat(lang(page(get("/fr" + NodeEntityType.path(node)).with(reader())))).isEqualTo("fr");
    }
}
