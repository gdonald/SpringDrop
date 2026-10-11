package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.rest.EntityRepresentation;
import dev.springdrop.kernel.rest.RestResourceConfig;
import dev.springdrop.kernel.rest.RestResourceManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class RestIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUMMARY = "summary";

    private static final String NODES = EntityRestController.PATH + "/" + NodeEntityType.ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestResourceManager resources;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void nodesServedOverRest() {
        clear();
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(FieldStorageConfig.single(SUMMARY, NodeEntityType.ID, StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        resources.save(new RestResourceConfig(NodeEntityType.ID, RestResourceConfig.ALL_METHODS,
                List.of("json", "xml")));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        resources.all().forEach(resource -> resources.delete(resource.entityType()));
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.findStorage(NodeEntityType.ID, SUMMARY)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, SUMMARY));
        types.all().forEach(type -> types.delete(type.id()));
    }

    private static RequestPostProcessor client(String... extra) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.create(ARTICLE), NodePermissions.editOwn(ARTICLE), NodePermissions.deleteOwn(ARTICLE),
                NodePermissions.VIEW_OWN_UNPUBLISHED));
        for (String method : RestResourceConfig.ALL_METHODS) {
            granted.add(RestResourceConfig.permission(method, NodeEntityType.ID));
        }
        granted.addAll(List.of(extra));
        return user(new AccountPrincipal(5L, "client", "", true, granted));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, RequestPostProcessor who)
            throws Exception {
        return mockMvc.perform(request.with(csrf()).with(who)).andReturn().getResponse();
    }

    private MockHttpServletResponse postJson(String json, RequestPostProcessor who) throws Exception {
        return send(post(NODES).contentType(MediaType.APPLICATION_JSON).content(json), who);
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    private long article(String title, boolean published, long owner) {
        return ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title,
                Map.of(SUMMARY, "Opening hours", BaseFieldDefinition.STATUS, published,
                        BaseFieldDefinition.OWNER, owner)), owner).id()).longValue();
    }

    @Test
    void aNodeIsCreatedAndReadOverRest() throws Exception {
        MockHttpServletResponse created = postJson("""
                {"bundle": "article", "label": "Spring schedule",
                 "fields": {"summary": "Opening hours", "status": true}}""", client());

        assertThat(created.getStatus()).isEqualTo(201);
        JsonNode body = json(created);
        long id = body.get("id").asLong();
        assertThat(created.getHeader("Location")).isEqualTo(NODES + "/" + id);
        assertThat(body.get("fields").get("owner").asLong()).isEqualTo(5L);

        MockHttpServletResponse read = send(get(NODES + "/" + id), client());
        assertThat(read.getStatus()).isEqualTo(200);
        assertThat(read.getContentType()).startsWith("application/json");
        assertThat(json(read).get("label").asString()).isEqualTo("Spring schedule");
        assertThat(json(read).get("fields").get(SUMMARY).asString()).isEqualTo("Opening hours");
        assertThat(nodes.find(id).orElseThrow().fields()).containsEntry(BaseFieldDefinition.STATUS, true);
    }

    @Test
    void aNodeIsReadAsXml() throws Exception {
        long id = article("Spring & summer", true, 5L);

        MockHttpServletResponse read = send(get(NODES + "/" + id).param(EntityRestController.FORMAT, "xml"),
                client());

        assertThat(read.getContentType()).startsWith("application/xml");
        var xml = Jsoup.parse(read.getContentAsString(), Parser.xmlParser());
        assertThat(xml.selectFirst("entity").attr("bundle")).isEqualTo(ARTICLE);
        assertThat(xml.selectFirst("label").text()).isEqualTo("Spring & summer");
        assertThat(xml.selectFirst("field[name=summary] value").text()).isEqualTo("Opening hours");
    }

    @Test
    void aNodeIsCreatedFromXml() throws Exception {
        MockHttpServletResponse created = send(post(NODES).contentType(MediaType.APPLICATION_XML).content("""
                <entity bundle="article"><label>Winter</label><fields><field name="summary"><value>Closed</value>
                </field><field name="status"><value>false</value></field></fields></entity>"""), client());

        assertThat(created.getStatus()).isEqualTo(201);
        long id = json(created).get("id").asLong();
        assertThat(nodes.find(id).orElseThrow().fields()).containsEntry(SUMMARY, "Closed")
                .containsEntry(BaseFieldDefinition.STATUS, false);
    }

    @Test
    void aNodeIsChangedAndDeleted() throws Exception {
        long id = article("Spring schedule", true, 5L);

        MockHttpServletResponse patched = send(patch(NODES + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\": \"Summer schedule\", \"fields\": {\"sticky\": \"true\"}}"), client());
        assertThat(patched.getStatus()).isEqualTo(200);
        assertThat(nodes.find(id).orElseThrow().label()).isEqualTo("Summer schedule");
        assertThat(nodes.find(id).orElseThrow().fields()).containsEntry(NodeEntityType.STICKY, true)
                .containsEntry(SUMMARY, "Opening hours");

        send(patch(NODES + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"), client());
        assertThat(nodes.find(id).orElseThrow().label()).isEqualTo("Summer schedule");

        assertThat(send(delete(NODES + "/" + id), client()).getStatus()).isEqualTo(204);
        assertThat(nodes.find(id)).isEmpty();
    }

    @Test
    void aMethodTheResourceDoesNotEnableIsRefused() throws Exception {
        resources.save(new RestResourceConfig(NodeEntityType.ID, List.of(RestResourceConfig.GET), List.of("json")));
        long id = article("Spring schedule", true, 5L);

        MockHttpServletResponse deleted = send(delete(NODES + "/" + id), client());

        assertThat(deleted.getStatus()).isEqualTo(404);
        assertThat(json(deleted).get("message").asString()).isEqualTo("No REST resource answers DELETE for node.");
        assertThat(nodes.find(id)).isPresent();
        assertThat(send(get(EntityRestController.PATH + "/node_type/1"), client()).getStatus()).isEqualTo(404);
    }

    @Test
    void aClientWithoutTheMethodsPermissionIsRefused() throws Exception {
        long id = article("Spring schedule", true, 5L);
        RequestPostProcessor reader = user(new AccountPrincipal(6L, "reader", "", true,
                List.of(NodePermissions.ACCESS_CONTENT, RestResourceConfig.permission("GET", NodeEntityType.ID))));

        assertThat(send(get(NODES + "/" + id), reader).getStatus()).isEqualTo(200);
        MockHttpServletResponse refused = send(delete(NODES + "/" + id), reader);
        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(json(refused).get("message").asString()).isEqualTo(
                "The restful delete entity:node permission is required.");
        assertThat(nodes.find(id)).isPresent();
    }

    @Test
    void entityAccessIsCheckedForEachMethod() throws Exception {
        long draft = article("Someone else's draft", false, 9L);

        assertThat(send(get(NODES + "/" + draft), client()).getStatus()).isEqualTo(403);
        assertThat(send(patch(NODES + "/" + draft).contentType(MediaType.APPLICATION_JSON).content("{}"), client())
                .getStatus()).isEqualTo(403);
        assertThat(send(delete(NODES + "/" + draft), client()).getStatus()).isEqualTo(403);
        RequestPostProcessor noCreate = user(new AccountPrincipal(6L, "reader", "", true,
                List.of(RestResourceConfig.permission("POST", NodeEntityType.ID))));
        assertThat(postJson("{\"bundle\": \"article\", \"label\": \"x\"}", noCreate).getStatus()).isEqualTo(403);
        assertThat(send(get(NODES + "/999999"), client()).getStatus()).isEqualTo(404);
    }

    @Test
    void theFirstAccountNeedsNoPermission() throws Exception {
        long id = article("Spring schedule", true, 5L);

        assertThat(send(get(NODES + "/" + id), user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "",
                true, List.of()))).getStatus()).isEqualTo(200);
    }

    @Test
    void badRequestsAreRefusedWithTheReason() throws Exception {
        long id = article("Spring schedule", true, 5L);

        assertThat(send(get(NODES + "/" + id).param(EntityRestController.FORMAT, "yaml"), client()).getStatus())
                .isEqualTo(406);
        assertThat(send(post(NODES).contentType(MediaType.TEXT_PLAIN).content("x"), client()).getStatus())
                .isEqualTo(415);
        assertThat(postJson("{not json", client()).getStatus()).isEqualTo(400);
        assertThat(send(post(NODES).contentType(MediaType.APPLICATION_JSON), client()).getStatus()).isEqualTo(400);
        assertThat(json(postJson("{\"label\": \"x\"}", client())).get("message").asString())
                .isEqualTo("The bundle is required.");
        assertThat(postJson("{\"bundle\": \" \", \"label\": \"x\"}", client()).getStatus()).isEqualTo(422);
        assertThat(json(postJson("{\"bundle\": \"article\", \"fields\": {\"owner\": 1}}", client()))
                .get("message").asString()).isEqualTo("The field owner cannot be written.");
        assertThat(json(postJson("{\"bundle\": \"article\", \"fields\": {\"revision_log\": \"x\"}}", client()))
                .get("message").asString()).isEqualTo("The field revision_log cannot be written.");
        assertThat(json(postJson("{\"bundle\": \"article\", \"fields\": {\"nope\": 1}}", client()))
                .get("message").asString()).isEqualTo("The field nope cannot be written.");
        assertThat(json(postJson("{\"bundle\": \"article\", \"fields\": {\"status\": \"maybe\"}}", client()))
                .get("message").asString()).isEqualTo("The value of status is not a true or false value.");
        assertThat(queries.query(NodeEntityType.ID).count()).isEqualTo(1);
    }

    @Test
    void anInvalidEntityIsRefusedWithItsViolations() throws Exception {
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary").asRequired());

        MockHttpServletResponse refused = postJson("{\"bundle\": \"article\", \"label\": \"x\"}", client());

        assertThat(refused.getStatus()).isEqualTo(422);
        assertThat(json(refused).get("errors").get(0).get("field").asString()).contains(SUMMARY);
    }

    @Test
    void anEntityTypeWithoutBundlesOrOwnersIsWrittenAsItIs() throws Exception {
        resources.save(new RestResourceConfig("user", RestResourceConfig.ALL_METHODS, List.of("json")));
        RequestPostProcessor first = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true,
                List.of()));

        MockHttpServletResponse created = send(post(EntityRestController.PATH + "/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\": \"rest-ada\", \"langcode\": \"fr\", \"fields\": {\"mail\": \"ada@rest.example\"}}"),
                first);
        assertThat(created.getStatus()).isEqualTo(201);
        JsonNode body = json(created);
        assertThat(body.get("langcode").asString()).isEqualTo("fr");
        assertThat(body.get("fields").has("owner")).isFalse();

        assertThat(send(delete(EntityRestController.PATH + "/user/" + body.get("id").asLong()), first).getStatus())
                .isEqualTo(204);
    }

    @Test
    void aConfigEntityTypeOrAFormatWithoutASerializerIsNotServed() throws Exception {
        resources.save(new RestResourceConfig(NodeEntityType.TYPE_ID, List.of("GET"), List.of("json")));
        resources.save(new RestResourceConfig(NodeEntityType.ID, List.of("GET"), List.of("json", "yaml")));
        long id = article("Spring schedule", true, 5L);
        RequestPostProcessor first = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true,
                List.of()));

        assertThat(send(get(EntityRestController.PATH + "/" + NodeEntityType.TYPE_ID + "/1"), first).getStatus())
                .isEqualTo(404);
        assertThat(send(get(NODES + "/" + id).param(EntityRestController.FORMAT, "yaml"), first).getStatus())
                .isEqualTo(406);
    }

    @Test
    void aNodeSentWithoutALabelIsCreatedUntitled() throws Exception {
        MockHttpServletResponse created = postJson("{\"bundle\": \"article\"}", client());

        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(json(created).get("label").asString()).isEmpty();
    }

    @Test
    void anEntityWithoutAUuidIsSentWithoutOne() {
        assertThat(EntityRepresentation.of(EntityData.of("node", 4L, ARTICLE, "x", Map.of())).uuid()).isNull();
    }

    @Test
    void theResourcesAreChosenOnTheAdministrationPage() throws Exception {
        RequestPostProcessor admin = user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_REST_RESOURCES));
        var page = Jsoup.parse(mockMvc.perform(get(RestResourceController.PATH).with(admin)).andReturn()
                .getResponse().getContentAsString());
        assertThat(page.selectFirst("input[name=node__GET]").hasAttr("checked")).isTrue();
        assertThat(page.select("tr[data-entity-type]").eachAttr("data-entity-type")).contains("node", "user");

        send(post(RestResourceController.PATH).param("node__GET", "true").param("user__GET", "true")
                .param("user__xml", "true"), admin);

        assertThat(resources.find(NodeEntityType.ID)).contains(new RestResourceConfig(NodeEntityType.ID,
                List.of("GET"), List.of("json")));
        assertThat(resources.find("user")).contains(new RestResourceConfig("user", List.of("GET"), List.of("xml")));
        assertThat(resources.find("comment")).isEmpty();
        assertThat(resources.permissions()).extracting(permission -> permission.name())
                .contains("restful get entity:node", "restful get entity:user");
    }
}
