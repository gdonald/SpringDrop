package dev.springdrop.kernel.views;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.handlers.BaseValueField;
import dev.springdrop.kernel.views.handlers.EntityFieldField;
import dev.springdrop.kernel.views.handlers.EntityLabelField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.MiniPager;
import dev.springdrop.kernel.views.handlers.NoneAccess;
import dev.springdrop.kernel.views.handlers.NonePager;
import dev.springdrop.kernel.views.handlers.OperationsField;
import dev.springdrop.kernel.views.handlers.PermissionAccess;
import dev.springdrop.kernel.views.handlers.ReferenceRelationship;
import dev.springdrop.kernel.views.handlers.SomePager;
import dev.springdrop.kernel.views.handlers.StandardSort;
import dev.springdrop.kernel.views.handlers.ValueArgument;
import dev.springdrop.kernel.views.handlers.ValueFilter;
import dev.springdrop.kernel.views.styles.UnformattedStyle;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class ViewExecutorIntegrationTest extends AbstractIntegrationTest {

    private static final String STORY = "view_story";

    private static final String NOTE = "view_note";

    @Autowired
    private ViewExecutor executor;

    @Autowired
    private NodeTypeManager nodeTypes;

    @Autowired
    private NodeService nodes;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private PluginRegistry registry;

    private long edith;

    private long frank;

    @BeforeEach
    void storiesByTwoAuthors() {
        accounts.install();
        nodeTypes.save(NodeType.of(STORY, "Story"));
        nodeTypes.save(NodeType.of(NOTE, "Note"));
        edith = accounts.create("edith", "edith@example.com", "").id();
        frank = accounts.create("frank", "frank@example.com", "").id();
        story("Bridges", edith, true, STORY);
        story("Apples", edith, true, STORY);
        story("Canals", frank, true, STORY);
        story("Drafts", edith, false, STORY);
        story("Errands", edith, true, NOTE);
        reader(NodePermissions.ACCESS_CONTENT, "administer user");
    }

    @AfterEach
    void removeEverything() {
        SecurityContextHolder.clearContext();
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        nodeTypes.delete(STORY);
        nodeTypes.delete(NOTE);
        entities.delete(UserEntityType.ID, edith);
        entities.delete(UserEntityType.ID, frank);
    }

    private static void reader(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(authentication(permissions));
    }

    private static Authentication authentication(String... permissions) {
        return new UsernamePasswordAuthenticationToken("reader", null,
                Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList());
    }

    private EntityData story(String title, long owner, boolean published, String type) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BaseFieldDefinition.STATUS, published);
        values.put(BaseFieldDefinition.OWNER, owner);
        values.put(BaseFieldDefinition.CREATED, OffsetDateTime.of(2026, 3, 4, 5, 6, 0, 0, ZoneOffset.UTC));
        return nodes.save(EntityData.of(NodeEntityType.ID, null, type, title, values), owner);
    }

    private static ViewOptions options(List<HandlerConfig> filters, List<HandlerConfig> sorts,
            List<HandlerConfig> arguments, List<HandlerConfig> relationships, PluginConfig pager) {
        return new ViewOptions(List.of(HandlerConfig.of("title", EntityLabelField.ID, "label", Map.of())), filters,
                sorts, arguments, relationships, pager, null, null, PluginConfig.of(NoneAccess.ID));
    }

    private static ViewConfig view(ViewOptions options) {
        return new ViewConfig("stories", "Stories", "", NodeEntityType.ID,
                List.of(new ViewDisplay(ViewDisplay.DEFAULT, "default", "Stories", options, Map.of())));
    }

    private static List<String> titles(ViewResult result) {
        return result.rows().stream().map(row -> row.entity().label()).toList();
    }

    private static HandlerConfig published() {
        return HandlerConfig.of("status", ValueFilter.ID, BaseFieldDefinition.STATUS,
                Map.of(FilterHandler.VALUE, "true", "type", "boolean"));
    }

    private static HandlerConfig byTitle() {
        return HandlerConfig.of("title", StandardSort.ID, "label", Map.of());
    }

    private static HandlerConfig author() {
        return HandlerConfig.of("author", ReferenceRelationship.ID, BaseFieldDefinition.OWNER, Map.of());
    }

    private static HandlerConfig ofType() {
        return HandlerConfig.of("type", ValueArgument.ID, NodeEntityType.BUNDLE_KEY, Map.of());
    }

    @Test
    void aViewAppliesAFilterASortARelationshipAndAContextualFilter() {
        HandlerConfig byEdith = HandlerConfig.of("author_name", ValueFilter.ID, "label",
                Map.of(FilterHandler.VALUE, "edith")).through("author");
        ViewConfig view = view(options(List.of(published(), byEdith), List.of(byTitle()), List.of(ofType()),
                List.of(author()), PluginConfig.of(NonePager.ID)));

        ViewResult result = executor.execute(view, ViewDisplay.DEFAULT, List.of(STORY), Map.of(), 1);

        assertThat(titles(result)).containsExactly("Apples", "Bridges");
        assertThat(result.rows()).allSatisfy(row -> assertThat(row.entity("author")).map(EntityData::label)
                .contains("edith"));
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void aSortOrdersDescendingAndOneThroughARelationshipIsPassedOver() {
        HandlerConfig descending = HandlerConfig.of("title", StandardSort.ID, "label", Map.of(SortHandler.ORDER,
                "desc"));
        HandlerConfig onAuthor = byTitle().through("author");
        ViewConfig view = view(options(List.of(published()), List.of(onAuthor, descending), List.of(ofType()),
                List.of(author()), PluginConfig.of(NonePager.ID)));

        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(STORY), Map.of(), 1)))
                .containsExactly("Canals", "Bridges", "Apples");
    }

    @Test
    void aContextualFilterWithoutAValueFollowsItsDefaultAction() {
        HandlerConfig empty = HandlerConfig.of("type", ValueArgument.ID, NodeEntityType.BUNDLE_KEY,
                Map.of(ArgumentHandler.DEFAULT_ACTION, ArgumentHandler.EMPTY));
        HandlerConfig fixed = HandlerConfig.of("type", ValueArgument.ID, NodeEntityType.BUNDLE_KEY,
                Map.of(ArgumentHandler.DEFAULT_ACTION, ArgumentHandler.FIXED, ArgumentHandler.DEFAULT_VALUE, NOTE));
        PluginConfig all = PluginConfig.of(NonePager.ID);

        assertThat(titles(executor.execute(view(options(List.of(published()), List.of(byTitle()), List.of(ofType()),
                List.of(), all)), ViewDisplay.DEFAULT, List.of(" "), Map.of(), 1)))
                .containsExactly("Apples", "Bridges", "Canals", "Errands");
        assertThat(titles(executor.execute(view(options(List.of(), List.of(), List.of(empty), List.of(), all)),
                ViewDisplay.DEFAULT, List.of(), Map.of(), 1))).isEmpty();
        assertThat(titles(executor.execute(view(options(List.of(), List.of(), List.of(fixed), List.of(), all)),
                ViewDisplay.DEFAULT, List.of(), Map.of(), 1))).containsExactly("Errands");
    }

    @Test
    void aContextualFilterGivenAValueThePropertyCannotHoldFindsNothing() {
        HandlerConfig byId = HandlerConfig.of("id", ValueArgument.ID, "id", Map.of("type", "number"));
        ViewConfig view = view(options(List.of(), List.of(), List.of(byId), List.of(),
                PluginConfig.of(NonePager.ID)));

        assertThat(executor.execute(view, ViewDisplay.DEFAULT, List.of("apples"), Map.of(), 1).rows()).isEmpty();
        assertThat(executor.execute(view, ViewDisplay.DEFAULT, List.of(String.valueOf(queries.query(
                NodeEntityType.ID).sort(Sort.ascending("id")).ids().getFirst())),
                Map.of(), 1).rows()).hasSize(1);
    }

    @Test
    void aContextualFilterThroughARelationshipNarrowsByTheEntityItReaches() {
        HandlerConfig authorId = HandlerConfig.of("author_id", ValueArgument.ID, "id", Map.of("type", "number"))
                .through("author");
        ViewConfig view = view(options(List.of(published()), List.of(byTitle()), List.of(authorId),
                List.of(author()), PluginConfig.of(NonePager.ID)));

        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(String.valueOf(frank)), Map.of(), 1)))
                .containsExactly("Canals");
    }

    @Test
    void anExposedFilterTakesItsValueFromTheReader() {
        HandlerConfig title = HandlerConfig.of("title_filter", ValueFilter.ID, "label", Map.of(
                FilterHandler.EXPOSED, true, FilterHandler.IDENTIFIER, "q", ValueFilter.OPERATOR, "contains"));
        ViewConfig view = view(options(List.of(published(), title), List.of(byTitle()), List.of(), List.of(),
                PluginConfig.of(NonePager.ID)));

        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of("q", "nal"), 1)))
                .containsExactly("Canals");
        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 1))).hasSize(4);
    }

    @Test
    void aFilterComparesInEachOfItsWays() {
        long bridges = queries.query(NodeEntityType.ID).condition(Condition.equal("label", "Bridges")).ids()
                .stream().mapToLong(id -> ((Number) id).longValue()).findFirst().orElseThrow();
        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("!=", List.of("Apples", "Canals", "Errands"));
        expected.put("<", List.of());
        expected.put(">", List.of("Apples", "Canals", "Errands"));
        expected.put("in", List.of("Bridges"));
        expected.put("=", List.of("Bridges"));
        for (Map.Entry<String, List<String>> each : expected.entrySet()) {
            String value = each.getKey().equals("in") ? bridges + ",, x, " : String.valueOf(bridges);
            HandlerConfig filter = HandlerConfig.of("id", ValueFilter.ID, "id",
                    Map.of(ValueFilter.OPERATOR, each.getKey(), FilterHandler.VALUE, value, "type", "number"));
            ViewConfig view = view(options(List.of(published(), filter), List.of(byTitle()), List.of(), List.of(),
                    PluginConfig.of(NonePager.ID)));

            assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 1)))
                    .as(each.getKey()).containsExactlyElementsOf(each.getValue());
        }
        HandlerConfig wrongType = HandlerConfig.of("id", ValueFilter.ID, "id",
                Map.of(FilterHandler.VALUE, "seven", "type", "number"));
        assertThat(executor.execute(view(options(List.of(wrongType), List.of(), List.of(), List.of(),
                PluginConfig.of(NonePager.ID))), ViewDisplay.DEFAULT, List.of(), Map.of(), 1).rows()).isEmpty();
    }

    @Test
    void aPagerShowsAPageAtATime() {
        PluginConfig two = new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 2));
        ViewConfig view = view(options(List.of(published()), List.of(byTitle()), List.of(), List.of(), two));

        ViewResult second = executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 2);
        ViewResult beyond = executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 9);

        assertThat(titles(second)).containsExactly("Canals", "Errands");
        assertThat(List.of(second.page(), second.totalPages())).containsExactly(2, 2);
        assertThat(beyond.page()).isEqualTo(2);
    }

    @Test
    void aFixedNumberOrAllResultsSkipTheOffsetAndShowNoPages() {
        PluginConfig some = new PluginConfig(SomePager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 2,
                PagerPlugin.OFFSET, 1));
        PluginConfig all = new PluginConfig(NonePager.ID, Map.of(PagerPlugin.OFFSET, 3));

        ViewResult first = executor.execute(view(options(List.of(published()), List.of(byTitle()), List.of(),
                List.of(), some)), ViewDisplay.DEFAULT, List.of(), Map.of(), 3);
        ViewResult rest = executor.execute(view(options(List.of(published()), List.of(byTitle()), List.of(),
                List.of(), all)), ViewDisplay.DEFAULT, List.of(), Map.of(), 1);

        assertThat(titles(first)).containsExactly("Bridges", "Canals");
        assertThat(first.totalPages()).isEqualTo(1);
        assertThat(titles(rest)).containsExactly("Errands");
    }

    @Test
    void withoutAPagerOrWithOneTheSiteLacksEveryResultIsShown() {
        ViewConfig noPager = view(options(List.of(published()), List.of(byTitle()), List.of(), List.of(), null));
        ViewConfig missing = view(options(List.of(published()), List.of(byTitle()), List.of(), List.of(),
                PluginConfig.of("retired_pager")));
        ViewConfig nothing = view(options(List.of(HandlerConfig.of("none", ValueFilter.ID, "label",
                Map.of(FilterHandler.VALUE, "Nothing like it"))), List.of(), List.of(), List.of(), null));

        assertThat(titles(executor.execute(noPager, ViewDisplay.DEFAULT, List.of(), Map.of(), 1))).hasSize(4);
        assertThat(titles(executor.execute(missing, ViewDisplay.DEFAULT, List.of(), Map.of(), 1))).hasSize(4);
        assertThat(executor.execute(nothing, ViewDisplay.DEFAULT, List.of(), Map.of(), 1).totalPages()).isOne();
    }

    @Test
    void resultsTheReaderMayNotViewAreLeftOut() {
        reader("administer user");

        assertThat(executor.execute(view(options(List.of(), List.of(), List.of(), List.of(), null)),
                ViewDisplay.DEFAULT, List.of(), Map.of(), 1).rows()).isEmpty();
    }

    @Test
    void handlersWhosePluginsAreGoneArePassedOver() {
        HandlerConfig retired = HandlerConfig.of("x", "retired", "label", Map.of());
        ViewConfig view = view(new ViewOptions(List.of(), List.of(retired, published()), List.of(retired, byTitle()),
                List.of(retired), List.of(retired), null, null, null, PluginConfig.of("retired_access")));

        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of("a"), Map.of(), 1))).hasSize(4);
        assertThat(executor.mayAccess(view, ViewDisplay.DEFAULT, null)).isTrue();
    }

    @Test
    void aFullPagerShowingEveryResultHasOnePage() {
        PluginConfig everything = new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 0));
        ViewConfig view = view(options(List.of(published()), List.of(byTitle()), List.of(), List.of(), everything));

        ViewResult result = executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 1);

        assertThat(List.of(result.rows().size(), result.totalPages())).containsExactly(4, 1);
    }

    @Test
    void aTableSortedByAFieldOnARelationshipKeepsItsOwnOrder() {
        ViewConfig view = view(new ViewOptions(List.of(HandlerConfig.of("name", EntityLabelField.ID, "label",
                Map.of()).through("author")), List.of(published()), List.of(byTitle()), List.of(), List.of(author()),
                null, null, null, PluginConfig.of(NoneAccess.ID)));

        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(ViewExecutor.ORDER, "name",
                ViewExecutor.SORT, "desc"), 1))).containsExactly("Apples", "Bridges", "Canals", "Errands");
    }

    @Test
    void aFilterThroughARelationshipWhosePluginIsGoneAppliesToTheBaseEntity() {
        HandlerConfig retired = HandlerConfig.of("gone", "retired", BaseFieldDefinition.OWNER, Map.of());
        ViewConfig view = view(options(List.of(published().through("gone")), List.of(byTitle()), List.of(),
                List.of(retired), null));

        assertThat(titles(executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 1))).hasSize(4);
    }

    @Test
    void aRelationshipToSomethingGoneOrNotViewableReachesNothing() {
        reader(NodePermissions.ACCESS_CONTENT);
        HandlerConfig referenceField = HandlerConfig.of("related", ReferenceRelationship.ID, "field_related",
                Map.of());
        fields.createStorage(new FieldStorageConfig("field_related", NodeEntityType.ID, EntityReferenceFieldType.ID,
                1, Map.of(EntityReferenceFieldType.TARGET_TYPE, NodeEntityType.ID)));
        try {
            ViewConfig view = view(options(List.of(published()), List.of(byTitle()), List.of(), List.of(author(),
                    referenceField), null));

            List<ResultRow> rows = executor.execute(view, ViewDisplay.DEFAULT, List.of(), Map.of(), 1).rows();

            assertThat(rows).allSatisfy(row -> assertThat(row.related()).isEmpty());
        } finally {
            fields.deleteStorage(NodeEntityType.ID, "field_related");
        }
    }

    @Test
    void aReferenceRelationshipFindsItsTargetType() {
        ReferenceRelationship relationship = (ReferenceRelationship) registry.managerFor(RelationshipHandler.class)
                .get(ReferenceRelationship.ID);
        fields.createStorage(new FieldStorageConfig("field_related", NodeEntityType.ID, EntityReferenceFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of(EntityReferenceFieldType.TARGET_TYPE, "media")));
        try {
            assertThat(relationship.targetType(NodeEntityType.ID, author())).isEqualTo(UserEntityType.ID);
            assertThat(relationship.targetType(NodeEntityType.ID, HandlerConfig.of("r", ReferenceRelationship.ID,
                    "field_related", Map.of()))).isEqualTo("media");
            assertThat(relationship.targetType(NodeEntityType.ID, HandlerConfig.of("r", ReferenceRelationship.ID,
                    "label", Map.of()))).isEmpty();
            assertThat(relationship.targetType(NodeEntityType.ID, HandlerConfig.of("r", ReferenceRelationship.ID,
                    "anything", Map.of(ReferenceRelationship.TARGET_TYPE, "taxonomy_term"))))
                    .isEqualTo("taxonomy_term");
            EntityData holding = EntityData.of(NodeEntityType.ID, 1L, STORY, "x", Map.of("field_related",
                    List.of(4, 5), "empty", List.of(), "word", "four"));
            assertThat(relationship.targetId(holding, HandlerConfig.of("r", ReferenceRelationship.ID,
                    "field_related", Map.of()))).contains(4L);
            assertThat(relationship.targetId(holding, HandlerConfig.of("r", ReferenceRelationship.ID, "empty",
                    Map.of()))).isEmpty();
            assertThat(relationship.targetId(holding, HandlerConfig.of("r", ReferenceRelationship.ID, "word",
                    Map.of()))).isEmpty();
            assertThat(relationship.label()).isEqualTo("Reference");
        } finally {
            fields.deleteStorage(NodeEntityType.ID, "field_related");
        }
    }

    @Test
    void accessGoesByTheViewsAccessPlugin() {
        ViewConfig byPermission = view(options(List.of(), List.of(), List.of(), List.of(), null)
                .withAccess(new PluginConfig(PermissionAccess.ID, Map.of(PermissionAccess.PERMISSION, "see stories"))));

        assertThat(executor.mayAccess(byPermission, ViewDisplay.DEFAULT, authentication("see stories"))).isTrue();
        assertThat(executor.mayAccess(byPermission, ViewDisplay.DEFAULT, authentication("other"))).isFalse();
        assertThat(executor.mayAccess(byPermission, ViewDisplay.DEFAULT, null)).isFalse();
        assertThat(executor.mayAccess(view(options(List.of(), List.of(), List.of(), List.of(), null).withAccess(null)),
                ViewDisplay.DEFAULT, null)).isTrue();
        assertThat(executor.mayAccess(view(options(List.of(), List.of(), List.of(), List.of(), null)),
                ViewDisplay.DEFAULT, null)).isTrue();
    }

    @Test
    void fieldHandlersDrawEachResult() {
        fields.createStorage(new FieldStorageConfig("field_subtitle", NodeEntityType.ID, "string", 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("field_subtitle", NodeEntityType.ID, STORY, "Subtitle"));
        reader(NodePermissions.ACCESS_CONTENT, "administer user", NodePermissions.editAny(STORY));
        try {
            EntityData story = story("Fields & <things>", edith, true, STORY);
            Map<String, Object> values = new LinkedHashMap<>(story.fields());
            values.put("field_subtitle", "A <b>long</b> day");
            story = nodes.save(story.withFields(values), edith);
            ResultRow row = new ResultRow(story, Map.of("author", entities.load(UserEntityType.ID, edith)
                    .orElseThrow()));
            EntityData note = nodes.find(((Number) queries.query(NodeEntityType.ID).condition(
                    Condition.equal("label", "Errands")).ids().getFirst())
                    .longValue()).orElseThrow();
            var handlers = registry.managerFor(FieldHandler.class);

            String label = handlers.get(EntityLabelField.ID).render(row, HandlerConfig.of("t", EntityLabelField.ID,
                    "label", Map.of()));
            String unlinked = handlers.get(EntityLabelField.ID).render(row, HandlerConfig.of("t", EntityLabelField.ID,
                    "label", Map.of(EntityLabelField.LINK, false)));
            String authorName = handlers.get(EntityLabelField.ID).render(row, HandlerConfig.of("a",
                    EntityLabelField.ID, "label", Map.of()).through("author"));
            String missing = handlers.get(EntityLabelField.ID).render(row, HandlerConfig.of("a",
                    EntityLabelField.ID, "label", Map.of()).through("nothing"));
            String subtitle = handlers.get(EntityFieldField.ID).render(row, HandlerConfig.of("s", EntityFieldField.ID,
                    "field_subtitle", Map.of(FieldHandler.LABEL, "Subtitle")));
            String noteSubtitle = handlers.get(EntityFieldField.ID).render(new ResultRow(note, Map.of()),
                    HandlerConfig.of("s", EntityFieldField.ID, "field_subtitle", Map.of()));
            String edit = handlers.get(OperationsField.ID).render(row, HandlerConfig.of("o", OperationsField.ID,
                    "id", Map.of()));
            String noEdit = handlers.get(OperationsField.ID).render(row, HandlerConfig.of("o", OperationsField.ID,
                    "id", Map.of()).through("author"));

            assertThat(Jsoup.parseBodyFragment(label).selectFirst("a").attr("href"))
                    .isEqualTo(NodeEntityType.path(story.id()));
            assertThat(label).contains("Fields &amp; &lt;things&gt;");
            assertThat(unlinked).isEqualTo("Fields &amp; &lt;things&gt;");
            assertThat(Jsoup.parseBodyFragment(authorName).text()).isEqualTo("edith");
            assertThat(missing).isEmpty();
            assertThat(subtitle).contains("A &lt;b&gt;long&lt;/b&gt; day").doesNotContain("Subtitle");
            assertThat(noteSubtitle).isEmpty();
            assertThat(Jsoup.parseBodyFragment(edit).selectFirst("a.btn").attr("href"))
                    .isEqualTo(NodeEntityType.editPath(story.id()));
            assertThat(noEdit).isEmpty();
            assertThat(handlers.get(OperationsField.ID).heading(HandlerConfig.of("o", OperationsField.ID, "id",
                    Map.of()))).isEqualTo("Operations");
            assertThat(handlers.get(OperationsField.ID).heading(HandlerConfig.of("o", OperationsField.ID, "id",
                    Map.of(FieldHandler.LABEL, "Actions")))).isEqualTo("Actions");
            assertThat(handlers.get(BaseValueField.ID).heading(HandlerConfig.of("b", BaseValueField.ID, "status",
                    Map.of()))).isEqualTo("status");
        } finally {
            fields.deleteStorage(NodeEntityType.ID, "field_subtitle");
        }
    }

    @Test
    void baseValuesReadAsText() {
        EntityData story = EntityData.of(NodeEntityType.ID, 3L, STORY, "Apples", Map.of(BaseFieldDefinition.STATUS,
                true, BaseFieldDefinition.CREATED, OffsetDateTime.of(2026, 3, 4, 5, 6, 0, 0, ZoneOffset.UTC),
                "sticky", false, "rating", 4));
        FieldHandler base = registry.managerFor(FieldHandler.class).get(BaseValueField.ID);
        ResultRow row = new ResultRow(story, Map.of());

        assertThat(List.of("id", "label", "bundle", BaseFieldDefinition.STATUS, "sticky", BaseFieldDefinition.CREATED,
                "rating", "missing").stream().map(property -> base.render(row, HandlerConfig.of("b",
                BaseValueField.ID, property, Map.of()))).toList())
                .containsExactly("3", "Apples", STORY, "Yes", "No", "2026-03-04 05:06", "4", "");
        assertThat(base.render(row, HandlerConfig.of("b", BaseValueField.ID, "id", Map.of()).through("gone")))
                .isEmpty();
    }

    @Test
    void anExposedFilterOffersAControlForItsKind() {
        FilterHandler filter = registry.managerFor(FilterHandler.class).get(ValueFilter.ID);
        FormElement text = filter.exposedElement(HandlerConfig.of("t", ValueFilter.ID, "label", Map.of(
                FilterHandler.IDENTIFIER, "q")), "app");
        FormElement flag = filter.exposedElement(HandlerConfig.of("s", ValueFilter.ID, "status", Map.of(
                "type", "boolean", "label", "Published")), "true");

        assertThat(List.of(text.name(), text.label(), String.valueOf(text.value()))).containsExactly("q", "label",
                "app");
        assertThat(List.of(flag.name(), flag.label())).containsExactly("s", "Published");
        assertThat(flag.options()).extracting(option -> option.value()).containsExactly("", "true", "false");
        assertThat(filter.condition(HandlerConfig.of("s", ValueFilter.ID, "status", Map.of("type", "boolean")),
                "maybe")).hasValueSatisfying(condition -> assertThat(condition.toString()).contains("IN"));
    }

    @Test
    void thePluginsDescribeThemselves() {
        assertThat(List.of(
                registry.managerFor(FieldHandler.class).get(EntityLabelField.ID).label(),
                registry.managerFor(FieldHandler.class).get(EntityFieldField.ID).label(),
                registry.managerFor(FieldHandler.class).get(BaseValueField.ID).label(),
                registry.managerFor(FieldHandler.class).get(OperationsField.ID).label(),
                registry.managerFor(FilterHandler.class).get(ValueFilter.ID).label(),
                registry.managerFor(SortHandler.class).get(StandardSort.ID).label(),
                registry.managerFor(ArgumentHandler.class).get(ValueArgument.ID).label(),
                registry.managerFor(PagerPlugin.class).get(NonePager.ID).label(),
                registry.managerFor(PagerPlugin.class).get(SomePager.ID).label(),
                registry.managerFor(PagerPlugin.class).get(FullPager.ID).label(),
                registry.managerFor(PagerPlugin.class).get(MiniPager.ID).label(),
                registry.managerFor(AccessPlugin.class).get(NoneAccess.ID).label(),
                registry.managerFor(AccessPlugin.class).get(PermissionAccess.ID).label()))
                .doesNotContain("");
        assertThat(registry.managerFor(StylePlugin.class).get(UnformattedStyle.ID).settingsForm("p_", Map.of()))
                .isEmpty();
        assertThat(registry.managerFor(StylePlugin.class).get(UnformattedStyle.ID).settingsValues("p_", Map.of()))
                .isEmpty();
    }

    @Test
    void pagersLinkThePagesTheirWay() {
        var pagers = registry.managerFor(PagerPlugin.class);

        assertThat(pagers.get(FullPager.ID).links(2, 3, page -> "/s?page=" + page)).hasValueSatisfying(pager ->
                assertThat(pager.pages()).hasSize(3));
        assertThat(pagers.get(FullPager.ID).links(1, 1, page -> "")).isEmpty();
        assertThat(pagers.get(MiniPager.ID).links(2, 3, page -> "/s?page=" + page)).hasValueSatisfying(pager -> {
            assertThat(pager.pages()).isEmpty();
            assertThat(pager.previousUrl()).contains("/s?page=1");
            assertThat(pager.nextUrl()).contains("/s?page=3");
        });
        assertThat(pagers.get(MiniPager.ID).links(1, 1, page -> "")).isEmpty();
        assertThat(pagers.get(SomePager.ID).links(1, 3, page -> "")).isEmpty();
        assertThat(pagers.get(NonePager.ID).links(1, 3, page -> "")).isEmpty();
        assertThat(pagers.get(FullPager.ID).itemsPerPage(new PluginConfig(FullPager.ID, Map.of(
                PagerPlugin.ITEMS_PER_PAGE, "lots")))).isEqualTo(10);
    }
}
