package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.jsonapi.JsonApiDocuments;
import dev.springdrop.kernel.jsonapi.JsonApiSettings;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.taxonomy.TaxonomyService;
import dev.springdrop.kernel.taxonomy.Vocabulary;
import dev.springdrop.kernel.taxonomy.VocabularyManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(JsonApiIntegrationTest.InternalFields.class)
class JsonApiIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String TAGS = "tags";

    private static final String SUMMARY = "summary";

    private static final String PRIORITY = "priority";

    private static final String ARTICLES = JsonApiController.PATH + "/node/article";

    private static final String MEDIA_TYPE = JsonApiDocuments.MEDIA_TYPE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private TaxonomyService taxonomy;

    @Autowired
    private VocabularyManager vocabularies;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ConfigStore config;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String INTERNAL_NOTE = "internal_note";

    private static final String REVIEWED_BY = "reviewed_by";

    private static final String VIEW_INTERNAL_FIELDS = "view internal fields";

    /** Hides two fields from anyone without a permission to see them. */
    @TestConfiguration
    static class InternalFields {

        @Bean
        FieldAccessRule internalFieldsRule() {
            return (type, fieldName, operation, entity, who) -> List.of(INTERNAL_NOTE, REVIEWED_BY).contains(fieldName)
                    && who.getAuthorities().stream().noneMatch(authority -> authority.getAuthority()
                            .equals(VIEW_INTERNAL_FIELDS)) ? AccessResult.forbid() : AccessResult.neutral();
        }
    }

    private EntityData weather;

    private EntityData sports;

    @BeforeEach
    void taggedArticles() {
        clear();
        vocabularies.save(Vocabulary.of(TAGS, "Tags"));
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(FieldStorageConfig.single(SUMMARY, NodeEntityType.ID, StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        fields.createStorage(FieldStorageConfig.single(PRIORITY, NodeEntityType.ID, IntegerFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(PRIORITY, NodeEntityType.ID, ARTICLE, "Priority"));
        fields.createStorage(new FieldStorageConfig(TAGS, NodeEntityType.ID, EntityReferenceFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of(EntityReferenceFieldType.TARGET_TYPE, TaxonomyEntityType.ID)));
        fields.createInstance(FieldInstanceConfig.of(TAGS, NodeEntityType.ID, ARTICLE, "Tags"));
        weather = term("Weather", true);
        sports = term("Sports", true);
        config.save(JsonApiSettings.CONFIG_NAME, new JsonApiSettings(false));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        config.delete(JsonApiSettings.CONFIG_NAME);
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        queries.query(TaxonomyEntityType.ID).ids().forEach(id -> taxonomy.delete(((Number) id).longValue()));
        List.of(SUMMARY, PRIORITY, TAGS).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        types.all().forEach(type -> types.delete(type.id()));
        vocabularies.all().forEach(vocabulary -> vocabularies.delete(vocabulary.id()));
    }

    private EntityData term(String name, boolean published) {
        return taxonomy.save(EntityData.of(TaxonomyEntityType.ID, null, TAGS, name,
                Map.of(BaseFieldDefinition.STATUS, published)));
    }

    private EntityData article(String title, int priority, List<EntityData> tags, boolean published, long owner) {
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, Map.of(SUMMARY, "Opening hours",
                PRIORITY, priority, TAGS, new ArrayList<>(tags.stream().map(EntityData::id).toList()),
                BaseFieldDefinition.STATUS, published, BaseFieldDefinition.OWNER, owner)), owner);
    }

    private static RequestPostProcessor editor(String... extra) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.create(ARTICLE), NodePermissions.editOwn(ARTICLE), NodePermissions.deleteOwn(ARTICLE)));
        granted.addAll(List.of(extra));
        return user(new AccountPrincipal(5L, "editor", "", true, granted));
    }

    private static RequestPostProcessor first() {
        return user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true, List.of()));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request, RequestPostProcessor who)
            throws Exception {
        return mockMvc.perform(request.with(csrf()).with(who)).andReturn().getResponse();
    }

    private JsonNode json(MockHttpServletResponse response) throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }

    private MockHttpServletResponse postArticle(String body, RequestPostProcessor who) throws Exception {
        return send(post(ARTICLES).contentType(MEDIA_TYPE).content(body), who);
    }

    private MockHttpServletResponse patchArticle(EntityData article, String body, RequestPostProcessor who)
            throws Exception {
        return send(patch(ARTICLES + "/" + article.uuid()).contentType(MEDIA_TYPE).content(body), who);
    }

    private static String detail(JsonNode document) {
        return document.get("errors").get(0).get("detail").asString();
    }

    private static List<String> labels(JsonNode data) {
        List<String> labels = new ArrayList<>();
        data.forEach(resource -> labels.add(resource.get("attributes").get("label").asString()));
        return labels;
    }

    @Test
    void aQueryFiltersSortsPaginatesAndIncludesAReferencedEntityInOneRequest() throws Exception {
        article("Alpha", 1, List.of(weather), true, 5L);
        article("Bravo", 2, List.of(weather, sports), true, 5L);
        article("Charlie", 3, List.of(sports), true, 5L);
        article("Delta", 4, List.of(weather), true, 5L);

        MockHttpServletResponse response = send(get(ARTICLES)
                .param("filter[tags.id]", weather.uuid().toString())
                .param("sort", "-priority")
                .param("page[limit]", "2")
                .param("include", TAGS)
                .param("fields[node--article]", "label,priority,tags"), editor());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(MEDIA_TYPE);
        JsonNode document = json(response);
        assertThat(labels(document.get("data"))).containsExactly("Delta", "Bravo");
        JsonNode delta = document.get("data").get(0);
        assertThat(delta.get("type").asString()).isEqualTo("node--article");
        assertThat(delta.get("attributes").propertyNames()).containsExactly("label", PRIORITY);
        assertThat(delta.get("relationships").get(TAGS).get("data").get(0).get("id").asString())
                .isEqualTo(weather.uuid().toString());
        assertThat(document.get("included")).hasSize(2);
        assertThat(labels(document.get("included"))).containsExactly("Weather", "Sports");
        assertThat(document.get("meta").get("count").asLong()).isEqualTo(3);
        assertThat(document.get("links").get("next").get("href").asString()).contains("page%5Boffset%5D=2");
        assertThat(document.get("links").has("prev")).isFalse();
    }

    @Test
    void aWriteRespectsFieldAccess() throws Exception {
        String sticky = """
                {"data": {"type": "node--article", "attributes": {"label": "Spring schedule", "sticky": true}}}""";

        MockHttpServletResponse refused = postArticle(sticky, editor());

        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(detail(json(refused))).isEqualTo("You may not edit the field sticky.");
        assertThat(json(refused).get("errors").get(0).get("source").get("pointer").asString())
                .isEqualTo("/data/attributes/sticky");
        assertThat(queries.query(NodeEntityType.ID).count()).isZero();

        MockHttpServletResponse created = postArticle(sticky, editor(NodePermissions.ADMINISTER_NODES));
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(json(created).get("data").get("attributes").get(NodeEntityType.STICKY).asBoolean()).isTrue();
    }

    @Nested
    class Reading {

        @Test
        void theEntryPointLinksToEveryResourceType() throws Exception {
            JsonNode links = json(send(get(JsonApiController.PATH), editor())).get("links");

            assertThat(links.get("node--article").get("href").asString()).isEqualTo("http://localhost" + ARTICLES);
            assertThat(links.has("taxonomy_term--tags")).isTrue();
            assertThat(links.has("user--user")).isTrue();
            assertThat(links.has("node_type--node_type")).isTrue();
        }

        @Test
        void aResourceIsReadByItsUuidWithItsIncludes() throws Exception {
            EntityData alpha = article("Alpha", 1, List.of(weather), true, 5L);

            JsonNode document = json(send(get(ARTICLES + "/" + alpha.uuid()).param("include", "tags,owner"),
                    editor()));

            JsonNode data = document.get("data");
            assertThat(data.get("id").asString()).isEqualTo(alpha.uuid().toString());
            assertThat(data.get("attributes").get("drupal_internal__id").asLong()).isEqualTo(alpha.id());
            assertThat(data.get("attributes").get(SUMMARY).asString()).isEqualTo("Opening hours");
            assertThat(data.get("attributes").has(BaseFieldDefinition.OWNER)).isFalse();
            assertThat(data.get("relationships").get(BaseFieldDefinition.OWNER).get("data").isNull()).isTrue();
            assertThat(data.get("links").get("self").get("href").asString())
                    .isEqualTo("http://localhost" + ARTICLES + "/" + alpha.uuid());
            assertThat(labels(document.get("included"))).containsExactly("Weather");
            assertThat(document.has("meta")).isFalse();
        }

        @Test
        void anArticleWithoutTagsHasAnEmptyRelationship() throws Exception {
            EntityData alpha = nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Alpha",
                    Map.of(BaseFieldDefinition.STATUS, true, BaseFieldDefinition.OWNER, 5L)), 5L);

            JsonNode data = json(send(get(ARTICLES + "/" + alpha.uuid()), editor())).get("data");

            assertThat(data.get("relationships").get(TAGS).get("data")).isEmpty();
        }

        @Test
        void aResourceThatIsNotThereIsNotFound() throws Exception {
            article("Alpha", 1, List.of(), true, 5L);

            MockHttpServletResponse missing = send(get(ARTICLES + "/" + java.util.UUID.randomUUID()), editor());
            assertThat(missing.getStatus()).isEqualTo(404);
            assertThat(missing.getContentType()).isEqualTo(MEDIA_TYPE);
            assertThat(json(missing).get("errors").get(0).get("status").asString()).isEqualTo("404");
            assertThat(send(get(ARTICLES + "/7"), editor()).getStatus()).isEqualTo(404);
            assertThat(send(get(JsonApiController.PATH + "/node/page"), editor()).getStatus()).isEqualTo(404);
            assertThat(send(get(JsonApiController.PATH + "/user/admin"), editor()).getStatus()).isEqualTo(404);
            assertThat(detail(json(send(get(JsonApiController.PATH + "/nothing/nothing"), editor()))))
                    .isEqualTo("There is no resource type nothing--nothing.");
        }

        @Test
        void aResourceThePersonMayNotViewIsForbiddenOrLeftOutOfACollection() throws Exception {
            EntityData draft = article("Someone else's draft", 1, List.of(), false, 9L);
            article("Alpha", 1, List.of(), true, 5L);

            assertThat(send(get(ARTICLES + "/" + draft.uuid()), editor()).getStatus()).isEqualTo(403);
            JsonNode document = json(send(get(ARTICLES), editor()));
            assertThat(labels(document.get("data"))).containsExactly("Alpha");
            assertThat(document.get("meta").get("omitted").get("resources").get(0).get("id").asString())
                    .isEqualTo(draft.uuid().toString());
        }

        @Test
        void anIncludedResourceThePersonMayNotViewIsLeftOut() throws Exception {
            EntityData hidden = term("Hidden", false);
            article("Alpha", 1, List.of(hidden, weather), true, 5L);
            article("Bravo", 2, List.of(hidden), true, 5L);

            JsonNode document = json(send(get(ARTICLES).param("include", TAGS), editor()));

            assertThat(labels(document.get("included"))).containsExactly("Weather");
            assertThat(document.get("meta").get("omitted").get("resources")).hasSize(1);
            assertThat(document.get("meta").get("omitted").get("resources").get(0).get("id").asString())
                    .isEqualTo(hidden.uuid().toString());
        }

        @Test
        void anIncludeFollowsDottedPathsAndNamesEachResourceOnce() throws Exception {
            fields.createStorage(new FieldStorageConfig("related", NodeEntityType.ID, EntityReferenceFieldType.ID,
                    FieldStorageConfig.UNLIMITED, Map.of(EntityReferenceFieldType.TARGET_TYPE, NodeEntityType.ID)));
            fields.createInstance(FieldInstanceConfig.of("related", NodeEntityType.ID, ARTICLE, "Related"));
            try {
                EntityData alpha = article("Alpha", 1, List.of(weather), true, 5L);
                nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Bravo", Map.of(TAGS,
                        List.of(weather.id()), "related", List.of(alpha.id()), BaseFieldDefinition.STATUS, true,
                        BaseFieldDefinition.OWNER, 5L)), 5L);

                JsonNode document = json(send(get(ARTICLES).param("filter[label]", "Bravo")
                        .param("include", "tags,related,related.tags"), first()));

                assertThat(labels(document.get("included"))).containsExactly("Weather", "Alpha");
            } finally {
                fields.deleteStorage(NodeEntityType.ID, "related");
            }
        }

        @Test
        void fieldsThePersonMayNotViewAreLeftOutAndNotIncluded() throws Exception {
            fields.createStorage(FieldStorageConfig.single(INTERNAL_NOTE, NodeEntityType.ID, StringFieldType.ID));
            fields.createInstance(FieldInstanceConfig.of(INTERNAL_NOTE, NodeEntityType.ID, ARTICLE, "Note"));
            fields.createStorage(new FieldStorageConfig(REVIEWED_BY, NodeEntityType.ID, EntityReferenceFieldType.ID,
                    1, Map.of(EntityReferenceFieldType.TARGET_TYPE, TaxonomyEntityType.ID)));
            fields.createInstance(FieldInstanceConfig.of(REVIEWED_BY, NodeEntityType.ID, ARTICLE, "Reviewed by"));
            try {
                EntityData alpha = nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Alpha", Map.of(
                        INTERNAL_NOTE, "Check the dates", REVIEWED_BY, weather.id(), BaseFieldDefinition.STATUS, true,
                        BaseFieldDefinition.OWNER, 5L)), 5L);

                JsonNode hidden = json(send(get(ARTICLES + "/" + alpha.uuid()).param("include", REVIEWED_BY),
                        editor()));
                assertThat(hidden.get("data").get("attributes").has(INTERNAL_NOTE)).isFalse();
                assertThat(hidden.get("data").get("relationships").has(REVIEWED_BY)).isFalse();
                assertThat(hidden.has("included")).isFalse();

                JsonNode shown = json(send(get(ARTICLES + "/" + alpha.uuid()).param("include", REVIEWED_BY),
                        editor(VIEW_INTERNAL_FIELDS)));
                assertThat(shown.get("data").get("attributes").get(INTERNAL_NOTE).asString())
                        .isEqualTo("Check the dates");
                assertThat(labels(shown.get("included"))).containsExactly("Weather");
            } finally {
                fields.deleteStorage(NodeEntityType.ID, INTERNAL_NOTE);
                fields.deleteStorage(NodeEntityType.ID, REVIEWED_BY);
            }
        }

        @Test
        void anIncludeThatIsNotARelationshipIsRefused() throws Exception {
            MockHttpServletResponse refused = send(get(ARTICLES).param("include", "summary"), editor());

            assertThat(refused.getStatus()).isEqualTo(400);
            assertThat(detail(json(refused))).isEqualTo(
                    "summary is not a relationship of node, so summary cannot be included.");
            assertThat(send(get(ARTICLES).param("include", "tags.nothing"), editor()).getStatus()).isEqualTo(400);
        }

        @Test
        void collectionsPageForwardAndBack() throws Exception {
            for (int priority = 1; priority <= 5; priority++) {
                article("Article " + priority, priority, List.of(), true, 5L);
            }

            JsonNode middle = json(send(get(ARTICLES).param("page[offset]", "2").param("page[limit]", "2")
                    .param("sort", PRIORITY), editor()));

            assertThat(labels(middle.get("data"))).containsExactly("Article 3", "Article 4");
            assertThat(middle.get("links").get("prev").get("href").asString()).contains("page%5Boffset%5D=0");
            assertThat(middle.get("links").get("next").get("href").asString()).contains("page%5Boffset%5D=4");
            assertThat(middle.get("links").get("self").get("href").asString()).contains("page%5Boffset%5D=2");
        }

        @Test
        void filtersCompareAttributesWithEachOperatorAndJoinInGroups() throws Exception {
            article("Alpha", 1, List.of(), true, 5L);
            article("Bravo", 2, List.of(), true, 5L);
            article("Charlie", 3, List.of(), true, 5L);

            assertThat(labels(json(send(get(ARTICLES).param("filter[priority][value]", "1")
                    .param("filter[priority][operator]", ">").param("sort", "label"), editor())).get("data")))
                    .containsExactly("Bravo", "Charlie");
            assertThat(labels(json(send(get(ARTICLES).param("filter[priority][value]", "2")
                    .param("filter[priority][operator]", "<"), editor())).get("data"))).containsExactly("Alpha");
            assertThat(labels(json(send(get(ARTICLES).param("filter[label][value]", "Bravo")
                    .param("filter[label][operator]", "<>").param("sort", "label"), editor())).get("data")))
                    .containsExactly("Alpha", "Charlie");
            assertThat(labels(json(send(get(ARTICLES).param("filter[label][value]", "harl")
                    .param("filter[label][operator]", "CONTAINS"), editor())).get("data")))
                    .containsExactly("Charlie");
            assertThat(labels(json(send(get(ARTICLES).param("filter[priority][operator]", "IN")
                    .param("filter[priority][value][]", "1", "3").param("sort", "-label"), editor())).get("data")))
                    .containsExactly("Charlie", "Alpha");
            assertThat(labels(json(send(get(ARTICLES)
                    .param("filter[either][group][conjunction]", "OR")
                    .param("filter[a][condition][path]", "label").param("filter[a][condition][value]", "Alpha")
                    .param("filter[a][condition][memberOf]", "either")
                    .param("filter[c][condition][path]", "drupal_internal__id")
                    .param("filter[c][condition][operator]", "IN")
                    .param("filter[c][condition][value][]", "999999")
                    .param("filter[c][condition][memberOf]", "either"), editor())).get("data")))
                    .containsExactly("Alpha");
        }

        @Test
        void aRelationshipIsFilteredOnByItsIdWithEqualsOrIn() throws Exception {
            article("Alpha", 1, List.of(weather), true, 5L);
            article("Bravo", 2, List.of(sports), true, 5L);

            assertThat(labels(json(send(get(ARTICLES).param("filter[tags.id][operator]", "IN")
                    .param("filter[tags.id][value][]", weather.uuid().toString(), "not-a-uuid")
                    .param("sort", "label"), editor())).get("data"))).containsExactly("Alpha");
            assertThat(json(send(get(ARTICLES).param("filter[tags.id]", "not-a-uuid"), editor())).get("data"))
                    .isEmpty();
            assertThat(detail(json(send(get(ARTICLES).param("filter[tags]", "1"), editor()))))
                    .isEqualTo("The relationship tags is filtered on by its id, as tags.id.");
            assertThat(detail(json(send(get(ARTICLES).param("filter[tags.label]", "1"), editor()))))
                    .isEqualTo("The relationship tags is filtered on by its id, as tags.id.");
            assertThat(detail(json(send(get(ARTICLES).param("filter[tags.id][value]", "1")
                    .param("filter[tags.id][operator]", "<>"), editor()))))
                    .isEqualTo("The relationship tags is filtered on with = or IN.");
        }

        @Test
        void aFilterOrSortOnSomethingThatIsNotAnAttributeIsRefused() throws Exception {
            assertThat(detail(json(send(get(ARTICLES).param("filter[colorway]", "red"), editor()))))
                    .isEqualTo("There is no attribute colorway on node--article.");
            assertThat(send(get(ARTICLES).param("sort", "colorway"), editor()).getStatus()).isEqualTo(400);
            assertThat(detail(json(send(get(ARTICLES).param("filter[priority]", "high"), editor()))))
                    .isEqualTo("The filter value for priority is not a whole number.");
        }

        @Test
        void aFieldThePersonMayNotViewCannotBeFilteredOrSortedOn() throws Exception {
            MockHttpServletResponse refused = send(get(JsonApiController.PATH + "/user/user")
                    .param("filter[mail]", "admin@example.com"), editor());

            assertThat(refused.getStatus()).isEqualTo(403);
            assertThat(detail(json(refused))).isEqualTo("You may not filter or sort on mail.");
            assertThat(send(get(JsonApiController.PATH + "/user/user").param("sort", "mail"), editor()).getStatus())
                    .isEqualTo(403);
        }

        @Test
        void anAccountsPasswordHashIsNeverSent() throws Exception {
            JsonNode document = json(send(get(JsonApiController.PATH + "/user/user")
                    .param("filter[drupal_internal__id]", String.valueOf(UserAccount.ADMINISTRATOR_ID)), first()));

            JsonNode attributes = document.get("data").get(0).get("attributes");
            assertThat(attributes.has("mail")).isTrue();
            assertThat(attributes.has("password_hash")).isFalse();
        }
    }

    @Nested
    class ConfigEntities {

        private static final String NODE_TYPES = JsonApiController.PATH + "/node_type/node_type";

        @BeforeEach
        void threeContentTypes() {
            types.save(NodeType.of("page", "Basic page"));
            types.save(NodeType.of("event", "Event"));
        }

        @Test
        void aConfigCollectionIsFilteredSortedAndPaged() throws Exception {
            JsonNode sorted = json(send(get(NODE_TYPES).param("sort", "-label").param("page[limit]", "2"), first()));
            assertThat(labels(sorted.get("data"))).containsExactly("Event", "Basic page");
            assertThat(sorted.get("meta").get("count").asLong()).isEqualTo(3);
            assertThat(sorted.get("data").get(0).get("id").asString()).isEqualTo("event");

            assertThat(labels(json(send(get(NODE_TYPES).param("filter[label][value]", "Event")
                    .param("filter[label][operator]", "<>"), first())).get("data")))
                    .containsExactly("Article", "Basic page");
            assertThat(labels(json(send(get(NODE_TYPES).param("filter[label][value]", "ic pa")
                    .param("filter[label][operator]", "CONTAINS"), first())).get("data")))
                    .containsExactly("Basic page");
            assertThat(labels(json(send(get(NODE_TYPES).param("filter[drupal_internal__id][operator]", "IN")
                    .param("filter[drupal_internal__id][value][]", "page", "event"), first())).get("data")))
                    .containsExactly("Event", "Basic page");
            assertThat(labels(json(send(get(NODE_TYPES).param("filter[label][value]", "B")
                    .param("filter[label][operator]", ">").param("sort", "label"), first())).get("data")))
                    .containsExactly("Basic page", "Event");
            assertThat(labels(json(send(get(NODE_TYPES).param("filter[label][value]", "B")
                    .param("filter[label][operator]", "<"), first())).get("data"))).containsExactly("Article");
            assertThat(labels(json(send(get(NODE_TYPES).param("filter[label]", "Article")
                    .param("fields[node_type--node_type]", "label"), first())).get("data")))
                    .containsExactly("Article");
            assertThat(json(send(get(NODE_TYPES).param("page[offset]", "10"), first())).get("data")).isEmpty();
        }

        @Test
        void inMemoryFiltersHandleMissingValuesNumbersAndGroups() throws Exception {
            assertThat(json(send(get(NODE_TYPES).param("filter[colorway]", "red"), first())).get("data")).isEmpty();
            assertThat(json(send(get(NODE_TYPES).param("filter[colorway][value]", "red")
                    .param("filter[colorway][operator]", "<>"), first())).get("data")).hasSize(3);
            assertThat(json(send(get(NODE_TYPES).param("filter[e][group][conjunction]", "OR"), first()))
                    .get("data")).hasSize(3);
            assertThat(labels(json(send(get(NODE_TYPES)
                    .param("filter[e][group][conjunction]", "OR")
                    .param("filter[a][condition][path]", "label").param("filter[a][condition][value]", "Event")
                    .param("filter[a][condition][memberOf]", "e")
                    .param("filter[b][condition][path]", "label").param("filter[b][condition][value]", "Article")
                    .param("filter[b][condition][memberOf]", "e")
                    .param("sort", "colorway,label"), first())).get("data")))
                    .containsExactly("Article", "Event");
        }

        @Test
        void numbersInConfigCompareAsNumbers() {
            config.save("node_type.numbered", Map.of("id", "numbered", "label", "10"));
            config.save("node_type.small", Map.of("id", "small", "label", "9"));
            try {
                assertThat(labels(json(send(get(NODE_TYPES).param("filter[label][value]", "8")
                        .param("filter[label][operator]", ">").param("sort", "-label"), first())).get("data")))
                        .containsExactly("Event", "Basic page", "Article", "10", "9");
                assertThat(labels(json(send(get(NODE_TYPES).param("filter[label][value]", "A")
                        .param("filter[label][operator]", ">").param("sort", "label"), first())).get("data")))
                        .containsExactly("Article", "Basic page", "Event");
                assertThat(labels(json(send(get(NODE_TYPES).param("sort", "description,label"), first()))
                        .get("data"))).containsExactly("Article", "Basic page", "Event", "9", "10");
                assertThat(labels(json(send(get(NODE_TYPES).param("sort", "-description,label"), first()))
                        .get("data"))).containsExactly("9", "10", "Article", "Basic page", "Event");
            } catch (Exception unexpected) {
                throw new AssertionError(unexpected);
            } finally {
                config.delete("node_type.numbered");
                config.delete("node_type.small");
            }
        }

        @Test
        void aConfigEntityIsReadByItsMachineName() throws Exception {
            JsonNode data = json(send(get(NODE_TYPES + "/page"), first())).get("data");

            assertThat(data.get("type").asString()).isEqualTo("node_type--node_type");
            assertThat(data.get("attributes").get("drupal_internal__id").asString()).isEqualTo("page");
            assertThat(data.get("attributes").get("label").asString()).isEqualTo("Basic page");
            assertThat(data.has("relationships")).isFalse();
            assertThat(json(send(get(NODE_TYPES + "/page").param("fields[node_type--node_type]", "label"), first()))
                    .get("data").get("attributes").propertyNames()).containsExactly("label");
            assertThat(send(get(NODE_TYPES + "/nothing"), first()).getStatus()).isEqualTo(404);
        }

        @Test
        void aConfigEntityIsClosedToThoseWhoDoNotAdministerItsType() throws Exception {
            assertThat(send(get(NODE_TYPES + "/page"), editor()).getStatus()).isEqualTo(403);
            JsonNode document = json(send(get(NODE_TYPES), editor()));
            assertThat(document.get("data")).isEmpty();
            assertThat(document.get("meta").get("omitted").get("resources")).hasSize(3);
        }

        @Test
        void aConfigEntityHasNoRelationshipsAndIsNotWritten() throws Exception {
            assertThat(send(get(NODE_TYPES).param("include", "owner"), first()).getStatus()).isEqualTo(400);
            assertThat(send(get(NODE_TYPES + "/page").param("include", "owner"), first()).getStatus())
                    .isEqualTo(400);
            assertThat(send(post(NODE_TYPES).contentType(MEDIA_TYPE).content("{}"), first()).getStatus())
                    .isEqualTo(405);
            assertThat(send(patch(NODE_TYPES + "/page").contentType(MEDIA_TYPE).content("{}"), first()).getStatus())
                    .isEqualTo(405);
            MockHttpServletResponse refused = send(delete(NODE_TYPES + "/page"), first());
            assertThat(refused.getStatus()).isEqualTo(405);
            assertThat(detail(json(refused))).isEqualTo(
                    "node_type--node_type is a config entity type, which JSON:API serves read-only.");
        }
    }

    @Nested
    class Writing {

        @Test
        void anArticleIsCreatedChangedAndDeleted() throws Exception {
            MockHttpServletResponse created = postArticle("""
                    {"data": {"type": "node--article",
                      "attributes": {"label": "Spring schedule", "summary": "Opening hours", "priority": 2},
                      "relationships": {"tags": {"data": [{"type": "taxonomy_term--tags", "id": "%s"}]}}}}"""
                    .formatted(weather.uuid()), editor());

            assertThat(created.getStatus()).isEqualTo(201);
            JsonNode data = json(created).get("data");
            String uuid = data.get("id").asString();
            assertThat(created.getHeader("Location")).isEqualTo("http://localhost" + ARTICLES + "/" + uuid);
            EntityData saved = nodes.find(data.get("attributes").get("drupal_internal__id").asLong()).orElseThrow();
            assertThat(saved.fields()).containsEntry(BaseFieldDefinition.OWNER, 5L).containsEntry(SUMMARY,
                    "Opening hours");
            assertThat(((Number) saved.fields().get(TAGS)).longValue()).isEqualTo(weather.id());

            MockHttpServletResponse changed = patchArticle(saved, """
                    {"data": {"type": "node--article", "id": "%s", "attributes": {"label": "Summer schedule",
                      "summary": null, "langcode": "en"},
                      "relationships": {"tags": {"data": []}}}}""".formatted(uuid), editor());
            assertThat(changed.getStatus()).isEqualTo(200);
            EntityData updated = nodes.find(((Number) saved.id()).longValue()).orElseThrow();
            assertThat(updated.label()).isEqualTo("Summer schedule");
            assertThat(updated.fields()).doesNotContainKeys(SUMMARY, TAGS).containsEntry(PRIORITY, 2);
            assertThat(json(changed).get("data").get("attributes").get("label").asString())
                    .isEqualTo("Summer schedule");

            assertThat(patchArticle(saved, """
                    {"data": {"type": "node--article", "id": "%s",
                      "relationships": {"tags": {"data": [{"type": "taxonomy_term--tags", "id": "%s"}]}}}}"""
                    .formatted(uuid, sports.uuid()), editor()).getStatus()).isEqualTo(200);
            EntityData retagged = nodes.find(((Number) saved.id()).longValue()).orElseThrow();
            assertThat(retagged.label()).isEqualTo("Summer schedule");
            assertThat(((Number) retagged.fields().get(TAGS)).longValue()).isEqualTo(sports.id());

            assertThat(send(delete(ARTICLES + "/" + uuid), editor()).getStatus()).isEqualTo(204);
            assertThat(nodes.find(((Number) saved.id()).longValue())).isEmpty();
        }

        @Test
        void aSingleValuedRelationshipIsWrittenAsOneId() throws Exception {
            fields.createStorage(new FieldStorageConfig("lead_tag", NodeEntityType.ID, EntityReferenceFieldType.ID,
                    1, Map.of(EntityReferenceFieldType.TARGET_TYPE, TaxonomyEntityType.ID)));
            fields.createInstance(FieldInstanceConfig.of("lead_tag", NodeEntityType.ID, ARTICLE, "Lead tag"));
            try {
                JsonNode data = json(postArticle("""
                        {"data": {"type": "node--article", "relationships": {"lead_tag": {"data":
                          {"type": "taxonomy_term--tags", "id": "%s"}}}}}""".formatted(sports.uuid()), editor()))
                        .get("data");

                assertThat(data.get("relationships").get("lead_tag").get("data").get("id").asString())
                        .isEqualTo(sports.uuid().toString());
                assertThat(((Number) nodes.find(data.get("attributes").get("drupal_internal__id").asLong())
                        .orElseThrow().fields().get("lead_tag")).longValue()).isEqualTo(sports.id());
            } finally {
                fields.deleteStorage(NodeEntityType.ID, "lead_tag");
            }
        }

        @Test
        void anEntityTypeWithoutBundlesOrOwnersIsWrittenToo() throws Exception {
            MockHttpServletResponse created = send(post(JsonApiController.PATH + "/user/user").contentType(MEDIA_TYPE)
                    .content("""
                            {"data": {"type": "user--user", "attributes": {"label": "jsonapi-ada",
                              "langcode": "fr", "mail": "ada@jsonapi.example"}}}"""), first());

            assertThat(created.getStatus()).isEqualTo(201);
            JsonNode attributes = json(created).get("data").get("attributes");
            assertThat(attributes.get("langcode").asString()).isEqualTo("fr");
            entities.delete("user", attributes.get("drupal_internal__id").asLong());
        }

        @Test
        void writesAreRefusedWhileTheSiteIsReadOnly() throws Exception {
            EntityData alpha = article("Alpha", 1, List.of(), true, 5L);
            config.delete(JsonApiSettings.CONFIG_NAME);

            MockHttpServletResponse refused = postArticle("{}", editor());
            assertThat(refused.getStatus()).isEqualTo(405);
            assertThat(detail(json(refused))).isEqualTo("JSON:API is configured to accept only read operations.");
            assertThat(patchArticle(alpha, "{}", editor()).getStatus()).isEqualTo(405);
            assertThat(send(delete(ARTICLES + "/" + alpha.uuid()), editor()).getStatus()).isEqualTo(405);
            assertThat(nodes.find(((Number) alpha.id()).longValue())).isPresent();
        }

        @Test
        void eachWriteNeedsTheEntityAccessItStandsFor() throws Exception {
            EntityData others = article("Someone else's", 1, List.of(), true, 9L);
            RequestPostProcessor reader = user(new AccountPrincipal(6L, "reader", "", true,
                    List.of(NodePermissions.ACCESS_CONTENT)));

            assertThat(postArticle("{\"data\": {\"type\": \"node--article\"}}", reader).getStatus()).isEqualTo(403);
            assertThat(patchArticle(others, "{\"data\": {\"type\": \"node--article\", \"id\": \"" + others.uuid()
                    + "\"}}", editor()).getStatus()).isEqualTo(403);
            MockHttpServletResponse refused = send(delete(ARTICLES + "/" + others.uuid()), editor());
            assertThat(refused.getStatus()).isEqualTo(403);
            assertThat(detail(json(refused))).isEqualTo("You may not delete this node.");
        }

        @Test
        void aBodyThatIsNotAResourceObjectOfTheEndpointsTypeIsRefused() throws Exception {
            EntityData alpha = article("Alpha", 1, List.of(), true, 5L);

            assertThat(send(post(ARTICLES).contentType("application/json").content("{}"), editor()).getStatus())
                    .isEqualTo(415);
            assertThat(send(post(ARTICLES).contentType(MEDIA_TYPE + "; version=2").content("{}"), editor())
                    .getStatus()).isEqualTo(415);
            assertThat(detail(json(send(post(ARTICLES).content("{}"), editor())))).isEqualTo("A request body is sent "
                    + "as application/vnd.api+json, without media type parameters other than a charset.");
            assertThat(detail(json(postArticle("{not json", editor())))).isEqualTo("The request body is not JSON.");
            assertThat(send(post(ARTICLES).contentType(MEDIA_TYPE), editor()).getStatus()).isEqualTo(400);
            assertThat(detail(json(postArticle("{\"data\": []}", editor()))))
                    .isEqualTo("The request body needs a data object.");
            MockHttpServletResponse conflict = postArticle("{\"data\": {\"type\": \"node--page\"}}", editor());
            assertThat(conflict.getStatus()).isEqualTo(409);
            assertThat(detail(json(conflict))).isEqualTo(
                    "The resource type node--page does not match the endpoint's node--article.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"id\": \"x\"}}",
                    editor())))).isEqualTo("Ids are given by the site, not the client.");
            MockHttpServletResponse wrongId = patchArticle(alpha, "{\"data\": {\"type\": \"node--article\"}}",
                    editor());
            assertThat(wrongId.getStatus()).isEqualTo(409);
            assertThat(detail(json(wrongId))).isEqualTo("The resource id  does not match the endpoint's "
                    + alpha.uuid() + ".");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"attributes\": []}}",
                    editor())))).isEqualTo("The attributes member is not an object.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"relationships\": "
                    + "{\"tags\": []}}}", editor())))).isEqualTo("The relationship tags needs a data member.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"relationships\": "
                    + "{\"tags\": {}}}}", editor())))).isEqualTo("The relationship tags needs a data member.");
            assertThat(queries.query(NodeEntityType.ID).count()).isEqualTo(1);
        }

        @Test
        void aFieldThatCannotBeWrittenIsRefusedWithItsPointer() throws Exception {
            EntityData alpha = article("Alpha", 1, List.of(), true, 5L);

            JsonNode internal = json(postArticle("{\"data\": {\"type\": \"node--article\", \"attributes\": "
                    + "{\"drupal_internal__id\": 4}}}", editor()));
            assertThat(detail(internal)).isEqualTo("The attribute drupal_internal__id cannot be written.");
            assertThat(internal.get("errors").get(0).get("source").get("pointer").asString())
                    .isEqualTo("/data/attributes/drupal_internal__id");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"attributes\": "
                    + "{\"tags\": [1]}}}", editor()))))
                    .isEqualTo("The field tags is a relationship, written under relationships.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"relationships\": "
                    + "{\"summary\": {\"data\": null}}}}", editor())))).isEqualTo("There is no relationship summary.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"relationships\": "
                    + "{\"tags\": {\"data\": {\"type\": \"node--article\", \"id\": \"" + weather.uuid() + "\"}}}}}",
                    editor())))).isEqualTo("There is no node--article " + weather.uuid()
                            + " for the relationship tags.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"relationships\": "
                    + "{\"tags\": {\"data\": {}}}}}", editor()))))
                    .isEqualTo("There is no taxonomy_term  for the relationship tags.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"attributes\": "
                    + "{\"promote\": null}}}", editor(NodePermissions.ADMINISTER_NODES)))))
                    .isEqualTo("The field promote cannot be cleared.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"relationships\": "
                    + "{\"owner\": {\"data\": null}}}}", editor())))).isEqualTo("The field owner cannot be cleared.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"attributes\": "
                    + "{\"colorway\": null}}}", editor())))).isEqualTo("The field colorway cannot be cleared.");
            assertThat(detail(json(postArticle("{\"data\": {\"type\": \"node--article\", \"attributes\": "
                    + "{\"created\": \"2026-10-15T09:00:00Z\"}}}", editor()))))
                    .isEqualTo("The field created cannot be written.");
            assertThat(detail(json(patchArticle(alpha, "{\"data\": {\"type\": \"node--article\", \"id\": \""
                    + alpha.uuid() + "\", \"attributes\": {\"langcode\": \"fr\"}}}", editor()))))
                    .isEqualTo("The language of a saved entity cannot be changed.");
            assertThat(queries.query(NodeEntityType.ID).count()).isEqualTo(1);
        }

        @Test
        void anInvalidEntityIsRefusedWithEachViolation() throws Exception {
            fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary")
                    .asRequired());

            MockHttpServletResponse refused = postArticle("{\"data\": {\"type\": \"node--article\"}}", editor());

            assertThat(refused.getStatus()).isEqualTo(422);
            JsonNode error = json(refused).get("errors").get(0);
            assertThat(error.get("detail").asString()).startsWith(SUMMARY);
            assertThat(error.get("source").get("pointer").asString()).isEqualTo("/data/attributes/" + SUMMARY);
        }
    }

    @Nested
    class Settings {

        @Test
        void theAllowedOperationsAreChosenOnTheSettingsPage() throws Exception {
            RequestPostProcessor admin = user("admin").authorities(
                    new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
            var page = Jsoup.parse(send(get(JsonApiSettingsController.PATH), admin).getContentAsString());
            assertThat(page.selectFirst("input[value=read_write]").hasAttr("checked")).isTrue();

            send(post(JsonApiSettingsController.PATH).param(JsonApiSettingsController.OPERATIONS,
                    JsonApiSettingsController.READ_ONLY), admin);
            assertThat(config.read(JsonApiSettings.CONFIG_NAME, JsonApiSettings.class, null).readOnly()).isTrue();
            page = Jsoup.parse(send(get(JsonApiSettingsController.PATH), admin).getContentAsString());
            assertThat(page.selectFirst("input[value=read_only]").hasAttr("checked")).isTrue();

            send(post(JsonApiSettingsController.PATH).param(JsonApiSettingsController.OPERATIONS,
                    JsonApiSettingsController.READ_WRITE), admin);
            assertThat(config.read(JsonApiSettings.CONFIG_NAME, JsonApiSettings.class, null).readOnly()).isFalse();
        }

        @Test
        void theSettingsPageNeedsTheSiteConfigurationPermission() throws Exception {
            assertThat(send(get(JsonApiSettingsController.PATH), editor()).getStatus()).isEqualTo(403);
        }
    }
}
