package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.jsonapi.JsonApiDocuments;
import dev.springdrop.kernel.jsonapi.JsonApiSettings;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.rest.RestResourceConfig;
import dev.springdrop.kernel.rest.RestResourceManager;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.security.ApiAccessDeniedHandler;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.ApiToken;
import dev.springdrop.kernel.user.ApiTokenService;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthenticationIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String CLIENT_ROLE = "api_client";

    private static final String ARTICLES = JsonApiController.PATH + "/node/article";

    private static final String NEW_ARTICLE = """
            {"data": {"type": "node--article", "attributes": {"label": "Spring schedule"}}}""";

    private static final String CLIENT_ADDRESS = "203.0.113.9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiTokenService tokens;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private RoleManager roles;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private RestResourceManager resources;

    @Autowired
    private FloodService flood;

    @Autowired
    private ConfigStore config;

    @Autowired
    private ObjectMapper objectMapper;

    private long client;

    @BeforeEach
    void anApiClientAccount() {
        clear();
        types.save(NodeType.of(ARTICLE, "Article"));
        config.save(JsonApiSettings.CONFIG_NAME, new JsonApiSettings(false));
        roles.save(RoleConfig.of(CLIENT_ROLE, "API client", 5)
                .granting(NodePermissions.ACCESS_CONTENT)
                .granting(NodePermissions.create(ARTICLE))
                .granting(Permissions.USE_API_TOKENS));
        client = accounts.create("api-client", "api-client@example.com", "",
                Map.of(UserEntityType.ROLES, List.of(CLIENT_ROLE))).id();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        config.delete(JsonApiSettings.CONFIG_NAME);
        flood.clear(FloodSettings.API_TOKEN_IP, CLIENT_ADDRESS);
        resources.all().forEach(resource -> resources.delete(resource.entityType()));
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        accounts.findByName("api-client").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
        roles.find(CLIENT_ROLE).ifPresent(role -> roles.delete(role.id()));
        types.all().forEach(type -> types.delete(type.id()));
    }

    private static RequestPostProcessor fromClientAddress() {
        return request -> {
            request.setRemoteAddr(CLIENT_ADDRESS);
            return request;
        };
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(fromClientAddress())).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    @Test
    void anApiCallWithoutPermissionIsRefusedWithTheRightStatusAndATokenCallSucceeds() throws Exception {
        MockHttpServletResponse anonymous = send(post(ARTICLES).contentType(JsonApiDocuments.MEDIA_TYPE)
                .content(NEW_ARTICLE).with(csrf()));
        assertThat(anonymous.getStatus()).isEqualTo(401);
        assertThat(anonymous.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"SpringDrop\"");
        assertThat(json(anonymous).get("errors").get(0).get("status").asString()).isEqualTo("401");

        MockHttpServletResponse signedInWithoutPermission = send(post(ARTICLES)
                .contentType(JsonApiDocuments.MEDIA_TYPE).content(NEW_ARTICLE).with(csrf())
                .with(user(new AccountPrincipal(6L, "reader", "", true, List.of(NodePermissions.ACCESS_CONTENT)))));
        assertThat(signedInWithoutPermission.getStatus()).isEqualTo(403);

        String token = tokens.issue(client, "Deploy script").secret();
        MockHttpServletResponse created = send(bearer(post(ARTICLES), token)
                .contentType(JsonApiDocuments.MEDIA_TYPE).content(NEW_ARTICLE));
        assertThat(created.getStatus()).isEqualTo(201);
        EntityData saved = nodes.find(json(created).get("data").get("attributes").get("drupal_internal__id")
                .asLong()).orElseThrow();
        assertThat(saved.fields()).containsEntry(BaseFieldDefinition.OWNER, client);
        assertThat(tokens.tokensOf(client).getFirst().lastUsed()).isNotNull();
    }

    @Test
    void aRestCallFromSomeoneNotSignedInIsRefusedWithA401() throws Exception {
        resources.save(new RestResourceConfig(NodeEntityType.ID, List.of(RestResourceConfig.GET), List.of("json")));

        MockHttpServletResponse refused = send(get(EntityRestController.PATH + "/node/1"));

        assertThat(refused.getStatus()).isEqualTo(401);
        assertThat(refused.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"SpringDrop\"");
        assertThat(json(refused).get("message").asString())
                .isEqualTo("The restful get entity:node permission is required.");
    }

    @Nested
    class BearerTokens {

        @Test
        void aTokenThatIsNotValidIsRefusedWithA401() throws Exception {
            MockHttpServletResponse jsonApi = send(bearer(get(ARTICLES), "sd_nothing"));
            assertThat(jsonApi.getStatus()).isEqualTo(401);
            assertThat(jsonApi.getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .isEqualTo("Bearer realm=\"SpringDrop\", error=\"invalid_token\"");
            assertThat(jsonApi.getContentType()).isEqualTo(JsonApiDocuments.MEDIA_TYPE);
            assertThat(json(jsonApi).get("errors").get(0).get("detail").asString())
                    .isEqualTo("The bearer token is not valid, or its account is blocked.");

            MockHttpServletResponse rest = send(bearer(get(EntityRestController.PATH + "/node/1"), "sd_nothing"));
            assertThat(rest.getStatus()).isEqualTo(401);
            assertThat(json(rest).get("message").asString())
                    .isEqualTo("The bearer token is not valid, or its account is blocked.");
        }

        @Test
        void aBlockedAccountsTokenIsRefused() throws Exception {
            String token = tokens.issue(client, "Deploy script").secret();
            accounts.block(client);

            assertThat(send(bearer(get(ARTICLES), token)).getStatus()).isEqualTo(401);
        }

        @Test
        void anAddressSendingTooManyTokensThatAreNotValidIsHeldOff() throws Exception {
            for (int attempt = 0; attempt < FloodSettings.IP_THRESHOLD; attempt++) {
                send(bearer(get(ARTICLES), "sd_guess" + attempt));
            }
            String token = tokens.issue(client, "Deploy script").secret();

            MockHttpServletResponse held = send(bearer(get(ARTICLES), token));

            assertThat(held.getStatus()).isEqualTo(429);
            assertThat(json(held).get("errors").get(0).get("detail").asString())
                    .isEqualTo("Too many requests with bearer tokens that are not valid. Try again later.");
        }

        @Test
        void aBearerHeaderOutsideTheWebServiceEndpointsIsLeftAlone() throws Exception {
            assertThat(send(bearer(get("/"), "sd_nothing")).getStatus()).isEqualTo(200);
            assertThat(send(get(ARTICLES).header(HttpHeaders.AUTHORIZATION, "Basic YTpi")).getStatus())
                    .isEqualTo(200);
        }

        @Test
        void aTokenIsRevokedByItsAccountAloneAndWithTheAccount() {
            ApiToken token = tokens.issue(client, "Deploy script").token();

            assertThat(tokens.revoke(client + 1, token.id())).isFalse();
            assertThat(tokens.tokensOf(client)).extracting(ApiToken::label).containsExactly("Deploy script");
            tokens.issue(client, "Backup job");
            nodes.delete(((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Alpha",
                    Map.of(BaseFieldDefinition.OWNER, client)), client).id()).longValue());
            assertThat(tokens.tokensOf(client)).hasSize(2);

            entities.delete(UserEntityType.ID, client);

            assertThat(tokens.tokensOf(client)).isEmpty();
        }
    }

    @Nested
    class CookieWrites {

        private final RequestPostProcessor editor = user(new AccountPrincipal(5L, "editor", "", true,
                List.of(NodePermissions.ACCESS_CONTENT, NodePermissions.create(ARTICLE))));

        @Test
        void aCookieWriteWithoutTheCsrfTokenIsRefused() throws Exception {
            MockHttpServletResponse jsonApi = send(post(ARTICLES).contentType(JsonApiDocuments.MEDIA_TYPE)
                    .content(NEW_ARTICLE).with(editor));
            assertThat(jsonApi.getStatus()).isEqualTo(403);
            assertThat(json(jsonApi).get("errors").get(0).get("detail").asString())
                    .isEqualTo(ApiAccessDeniedHandler.MESSAGE);

            MockHttpServletResponse rest = send(post(EntityRestController.PATH + "/node")
                    .contentType("application/json").content("{}").with(editor));
            assertThat(rest.getStatus()).isEqualTo(403);
            assertThat(json(rest).get("message").asString()).isEqualTo(ApiAccessDeniedHandler.MESSAGE);
            assertThat(queries.query(NodeEntityType.ID).count()).isZero();
        }

        @Test
        void aCookieWriteCarryingTheSessionTokenGoesThrough() throws Exception {
            MockHttpSession session = new MockHttpSession();
            MockHttpServletResponse issued = send(get(SessionTokenController.PATH).session(session).with(editor));
            assertThat(issued.getContentType()).startsWith("text/plain");

            MockHttpServletResponse created = send(post(ARTICLES).session(session).with(editor)
                    .header("X-CSRF-TOKEN", issued.getContentAsString())
                    .contentType(JsonApiDocuments.MEDIA_TYPE).content(NEW_ARTICLE));

            assertThat(created.getStatus()).isEqualTo(201);
        }
    }

    @Nested
    class TokenPage {

        private RequestPostProcessor clientAccount() {
            return user(new AccountPrincipal(client, "api-client", "", true, List.of(Permissions.USE_API_TOKENS)));
        }

        private Document page(MockHttpServletRequestBuilder request) throws Exception {
            return Jsoup.parse(send(request.with(csrf()).with(clientAccount())).getContentAsString());
        }

        @Test
        void aTokenIsCreatedShownOnceListedAndRevoked() throws Exception {
            assertThat(page(get(ApiTokenController.PATH)).text()).contains("You have no API tokens.");

            Document created = page(post(ApiTokenController.PATH).param(ApiTokenController.LABEL, " Deploy script "));
            String secret = created.selectFirst("code.issued-token").text();
            assertThat(secret).startsWith(ApiTokenService.PREFIX);
            assertThat(created.select("table.api-tokens tbody tr td").first().text()).isEqualTo("Deploy script");
            assertThat(created.select("table.api-tokens tbody tr td").get(2).text()).isEqualTo("Never");
            assertThat(send(bearer(get(ARTICLES), secret)).getStatus()).isEqualTo(200);
            Document listed = page(get(ApiTokenController.PATH));
            assertThat(listed.selectFirst("code.issued-token")).isNull();
            assertThat(listed.select("table.api-tokens tbody tr td").get(2).text()).isNotEqualTo("Never");

            long id = tokens.tokensOf(client).getFirst().id();
            MockHttpServletResponse revoked = send(post(ApiTokenController.revokePath(id)).with(csrf())
                    .with(clientAccount()));

            assertThat(revoked.getRedirectedUrl()).isEqualTo(ApiTokenController.PATH);
            assertThat(tokens.tokensOf(client)).isEmpty();
            assertThat(send(bearer(get(ARTICLES), secret)).getStatus()).isEqualTo(401);
        }

        @Test
        void aLabelIsRequiredAndAtMost128Characters() throws Exception {
            Document blank = page(post(ApiTokenController.PATH).param(ApiTokenController.LABEL, ""));
            assertThat(blank.select(".invalid-feedback").text()).isNotBlank();
            Document tooLong = page(post(ApiTokenController.PATH).param(ApiTokenController.LABEL, "x".repeat(129)));
            assertThat(tooLong.select(".invalid-feedback").text()).isNotBlank();
            assertThat(tokens.tokensOf(client)).isEmpty();
        }

        @Test
        void thePageNeedsThePermissionAndAnAccount() throws Exception {
            assertThat(send(get(ApiTokenController.PATH).with(user(new AccountPrincipal(client, "api-client", "",
                    true, List.of())))).getStatus()).isEqualTo(403);
            assertThat(send(get(ApiTokenController.PATH).with(authentication(new AnonymousAuthenticationToken(
                    "visitor", "anonymousUser", List.of(new SimpleGrantedAuthority(Permissions.USE_API_TOKENS))))))
                    .getStatus()).isEqualTo(403);
        }
    }
}
