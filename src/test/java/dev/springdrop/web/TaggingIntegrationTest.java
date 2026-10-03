package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValueLookup;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.field.widget.types.EntityReferenceAutocompleteTagsWidget;
import dev.springdrop.kernel.field.widget.types.EntityReferenceAutocompleteWidget;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.taxonomy.TaxonomyPermissions;
import dev.springdrop.kernel.taxonomy.TaxonomyService;
import dev.springdrop.kernel.taxonomy.Vocabulary;
import dev.springdrop.kernel.taxonomy.VocabularyManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class TaggingIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String TAGS = "tags";

    private static final String FIELD = "field_tags";

    /** A node field that is not a reference, which tags nothing. */
    private static final String SUBTITLE = "field_subtitle";

    /** A node reference to something other than terms, which tags nothing. */
    private static final String REVIEWER = "field_reviewer";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ViewManager views;

    @Autowired
    private DefaultViews defaultViews;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private TaxonomyService taxonomy;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private VocabularyManager vocabularies;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private EntityValueLookup lookup;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private long weather;

    @BeforeEach
    void articlesTaggedFromATagsVocabulary() {
        menus.install();
        storedLinks.install();
        clear();
        vocabularies.save(Vocabulary.of(TAGS, "Tags").describedAs("Words that describe content."));
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(new FieldStorageConfig(FIELD, NodeEntityType.ID, EntityReferenceFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of(EntityReferenceFieldType.TARGET_TYPE, TaxonomyEntityType.ID)));
        fields.createInstance(FieldInstanceConfig.of(FIELD, NodeEntityType.ID, ARTICLE, "Tags").withSettings(Map.of(
                FieldWidgetManager.WIDGET_SETTING, EntityReferenceAutocompleteTagsWidget.ID,
                EntityReferenceAutocompleteWidget.AUTO_CREATE, true,
                EntityReferenceAutocompleteWidget.AUTO_CREATE_BUNDLE, TAGS)));
        fields.createStorage(FieldStorageConfig.single(SUBTITLE, NodeEntityType.ID, StringFieldType.ID));
        fields.createStorage(new FieldStorageConfig(REVIEWER, NodeEntityType.ID, EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, UserEntityType.ID)));
        weather = ((Number) taxonomy.save(EntityData.of(TaxonomyEntityType.ID, null, TAGS, "Weather",
                Map.of())).id()).longValue();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        queries.query(TaxonomyEntityType.ID).ids().forEach(id -> taxonomy.delete(((Number) id).longValue()));
        List.of(FIELD, SUBTITLE, REVIEWER).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        types.all().forEach(type -> types.delete(type.id()));
        vocabularies.all().forEach(vocabulary -> vocabularies.delete(vocabulary.id()));
    }

    private static RequestPostProcessor writer(String... extra) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.create(ARTICLE), NodePermissions.editOwn(ARTICLE)));
        granted.addAll(List.of(extra));
        return user(new AccountPrincipal(7L, "writer", "", true, granted));
    }

    private static RequestPostProcessor reader() {
        return user(new AccountPrincipal(8L, "reader", "", true, List.of(NodePermissions.ACCESS_CONTENT)));
    }

    private long tagged(String title, List<Long> terms, boolean published) {
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
        values.put(FIELD, new ArrayList<>(terms));
        values.put(BaseFieldDefinition.STATUS, published);
        return ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, values), 7L).id())
                .longValue();
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void taggingANodeIncludingANewTermPersistsAndTheTermPageListsItWithAFeed() throws Exception {
        String redirect = mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE)
                        .with(writer(TaxonomyPermissions.create(TAGS))).with(csrf())
                        .param(NodeController.TITLE, "Spring schedule")
                        .param(FIELD, "Weather (" + weather + "), \"Hours, opening\""))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        long node = Long.parseLong(redirect.substring("/node/".length()));

        List<Long> terms = ((List<?>) nodes.find(node).orElseThrow().fields().get(FIELD)).stream()
                .map(term -> ((Number) term).longValue())
                .toList();
        assertThat(terms).hasSize(2).first().isEqualTo(weather);
        long created = terms.get(1);
        assertThat(taxonomy.find(created)).hasValueSatisfying(term -> {
            assertThat(term.label()).isEqualTo("Hours, opening");
            assertThat(term.bundle()).isEqualTo(TAGS);
            assertThat(term.fields()).containsEntry(BaseFieldDefinition.STATUS, true);
        });

        Document termPage = page(TaxonomyEntityType.path(created), reader());
        assertThat(termPage.select(".term-content article.node--teaser h2").eachText())
                .containsExactly("Spring schedule");
        assertThat(termPage.selectFirst("head link[rel=alternate]").attr("href"))
                .isEqualTo(TaxonomyController.feedPath(created));

        Document feed = Jsoup.parse(mockMvc.perform(get(TaxonomyController.feedPath(created)).with(reader()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/rss+xml"))
                .andReturn().getResponse().getContentAsString(), "", Parser.xmlParser());
        assertThat(feed.select("channel > title").text()).isEqualTo("Hours, opening");
        assertThat(feed.select("item > title").eachText()).containsExactly("Spring schedule");
        assertThat(feed.selectFirst("item > link").text()).endsWith(NodeEntityType.path(node));
        assertThat(feed.select("item > pubDate")).hasSize(1);
    }

    @Nested
    class TheTagsInput {

        @Test
        void theEditFormHoldsEveryTagInOneInput() throws Exception {
            long hours = ((Number) taxonomy.save(EntityData.of(TaxonomyEntityType.ID, null, TAGS, "Hours, opening",
                    Map.of())).id()).longValue();
            long node = tagged("Spring schedule", List.of(weather, hours), true);

            Document form = page(NodeEntityType.editPath(node), writer());

            assertThat(form.select("input[name=" + FIELD + "]")).hasSize(1);
            assertThat(form.selectFirst("input[name=" + FIELD + "]").val())
                    .isEqualTo("Weather (" + weather + "), \"Hours, opening (" + hours + ")\"");
            assertThat(form.text()).contains("Separate entries with commas.");
            assertThat(form.select("button:contains(Add another item)")).isEmpty();
        }

        @Test
        void aNewTagIsNotCreatedForSomeoneWhoMayNotCreateTerms() throws Exception {
            Document form = Jsoup.parse(mockMvc.perform(post(NodeController.ADD_PATH + "/" + ARTICLE)
                            .with(writer()).with(csrf())
                            .param(NodeController.TITLE, "Spring schedule")
                            .param(FIELD, "Brand new"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());

            assertThat(form.selectFirst("input[name=" + FIELD + "]").hasClass("is-invalid")).isTrue();
            assertThat(taxonomy.termsIn(TAGS)).extracting(EntityData::label).containsExactly("Weather");
        }

        @Test
        void aRequiredFieldsDescriptionIsKeptBesideTheCommaHint() {
            fields.createInstance(FieldInstanceConfig.of(FIELD, NodeEntityType.ID, ARTICLE, "Tags")
                    .withDescription("Words for this article.")
                    .asRequired()
                    .withSettings(Map.of(FieldWidgetManager.WIDGET_SETTING, EntityReferenceAutocompleteTagsWidget.ID)));
            WidgetContext context = widgets.context(NodeEntityType.ID, ARTICLE, FIELD);
            FormElement input = widgets.build(context, List.of(), 0).children().getFirst();

            assertThat(input.description()).isEqualTo("Words for this article. Separate entries with commas.");
            assertThat(input.required()).isTrue();
        }

        @Test
        void theWidgetDrawsOneValueOrNoneThroughTheSameInput() {
            WidgetContext context = widgets.context(NodeEntityType.ID, ARTICLE, FIELD);
            FieldWidget widget = widgets.widget(context);

            assertThat(widget.id()).isEqualTo(EntityReferenceAutocompleteTagsWidget.ID);
            assertThat(widget.element(context, 0, weather).value()).isEqualTo("Weather (" + weather + ")");
            assertThat(widget.element(context, 0, null).value()).isEqualTo("");
            assertThat(widgets.extract(context, Map.of())).isEmpty();
        }

        @Test
        void aTagWithADoubleQuoteInItsNameIsQuoted() {
            long desk = ((Number) taxonomy.save(EntityData.of(TaxonomyEntityType.ID, null, TAGS,
                    "The \"main\" desk", Map.of())).id()).longValue();
            WidgetContext context = widgets.context(NodeEntityType.ID, ARTICLE, FIELD);

            assertThat(widgets.build(context, List.of(desk), 0).children().getFirst().value())
                    .isEqualTo("\"The \"\"main\"\" desk (" + desk + ")\"");
        }

        @Test
        void aReferenceToAConfigEntityIsCheckedByItsName() {
            assertThat(lookup.referenceExists(NodeEntityType.TYPE_ID, ARTICLE)).isTrue();
            assertThat(lookup.referenceExists(NodeEntityType.TYPE_ID, "missing")).isFalse();
            assertThat(lookup.referenceExists(TaxonomyEntityType.ID, "Weather")).isFalse();
        }
    }

    @Nested
    class TheTermPage {

        @Test
        void onlyPublishedContentThePersonMayReadIsListedNewestFirst() throws Exception {
            tagged("Older", List.of(weather), true);
            tagged("Draft", List.of(weather), false);
            tagged("Newer", List.of(weather), true);
            tagged("Untagged", List.of(), true);

            assertThat(page(TaxonomyEntityType.path(weather), reader())
                    .select(".term-content article.node--teaser h2").eachText()).containsExactly("Newer", "Older");
        }

        @Test
        void theListingIsPaged() throws Exception {
            for (int number = 1; number <= TaxonomyController.PER_PAGE + 1; number++) {
                tagged("Notice " + number, List.of(weather), true);
            }

            Document first = page(TaxonomyEntityType.path(weather), reader());
            Document second = page(TaxonomyEntityType.path(weather) + "?page=2", reader());

            assertThat(first.select(".term-content article.node--teaser")).hasSize(TaxonomyController.PER_PAGE);
            assertThat(second.select(".term-content article.node--teaser h2").eachText())
                    .containsExactly("Notice 1");
        }

        @Test
        void aSiteWithNoFieldTaggingContentListsNothing() throws Exception {
            fields.deleteStorage(NodeEntityType.ID, FIELD);

            assertThat(page(TaxonomyEntityType.path(weather), reader()).select(".term-content article")).isEmpty();
            assertThat(Jsoup.parse(mockMvc.perform(get(TaxonomyController.feedPath(weather)).with(reader()))
                    .andReturn().getResponse().getContentAsString(), "", Parser.xmlParser()).select("item")).isEmpty();
        }

        @Test
        void aSiteWithoutTheTermViewListsNothingOnTheTermPage() throws Exception {
            tagged("Older", List.of(weather), true);
            views.delete(DefaultViews.TAXONOMY_TERM);
            try {
                assertThat(page(TaxonomyEntityType.path(weather), reader()).select(".term-content article"))
                        .isEmpty();
            } finally {
                defaultViews.install();
            }
        }

        @Test
        void aFeedOfContentWithoutACreationDateLeavesThePublicationDateOut() throws Exception {
            entities.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Undated",
                    Map.of(FIELD, List.of(weather), BaseFieldDefinition.STATUS, true)));

            Document feed = Jsoup.parse(mockMvc.perform(get(TaxonomyController.feedPath(weather)).with(reader()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), "", Parser.xmlParser());

            assertThat(feed.select("item > title").eachText()).containsExactly("Undated");
            assertThat(feed.select("item > pubDate")).isEmpty();
        }
    }
}
