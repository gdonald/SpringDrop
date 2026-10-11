package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.action.plugins.DeleteNode;
import dev.springdrop.kernel.action.plugins.PublishNode;
import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchProgress;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.handlers.BulkFormField;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class BulkOperationsIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String PAGE = "page";

    private static final String CONTENT_PATH = "/admin/content";

    private static final String BULK_PATH = BulkForm.path(DefaultViews.CONTENT, "page_1");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ViewManager views;

    @Autowired
    private DefaultViews defaultViews;

    @Autowired
    private BatchManager batches;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private BulkForm bulkForm;

    @BeforeEach
    void articlesAndAPage() {
        accounts.install();
        clear();
        views.delete(DefaultViews.CONTENT);
        defaultViews.install();
        types.save(NodeType.of(ARTICLE, "Article"));
        types.save(NodeType.of(PAGE, "Basic page"));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
    }

    private EntityData node(String bundle, String title) {
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(bundle).orElseThrow(), 1L).fields());
        values.put(BaseFieldDefinition.STATUS, false);
        return nodes.save(EntityData.of(NodeEntityType.ID, null, bundle, title, values), 1L);
    }

    private static String item(EntityData node) {
        return NodeEntityType.ID + ":" + node.id();
    }

    private static RequestPostProcessor articleEditor() {
        return user(new AccountPrincipal(5L, "editor", "", true, List.of(DefaultViews.ACCESS_CONTENT_OVERVIEW,
                NodePermissions.ACCESS_CONTENT, NodePermissions.VIEW_ANY_UNPUBLISHED, NodePermissions.editAny(ARTICLE),
                NodePermissions.deleteAny(ARTICLE))));
    }

    private Document overview() throws Exception {
        return Jsoup.parse(mockMvc.perform(get(CONTENT_PATH).with(articleEditor())).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletResponse apply(String action, List<String> items, String... more) throws Exception {
        MockHttpServletRequestBuilder request = post(BULK_PATH).param(BulkForm.ACTION, action)
                .param(BulkForm.DESTINATION, CONTENT_PATH + "?status=0");
        items.forEach(value -> request.param(BulkFormField.ITEMS, value));
        for (int index = 0; index < more.length; index += 2) {
            request.param(more[index], more[index + 1]);
        }
        return mockMvc.perform(request.with(csrf()).with(articleEditor())).andReturn().getResponse();
    }

    /** Runs the batch the response started to its end, a chunk per request as the browser would. */
    private BatchProgress runBatch(MockHttpServletResponse started) throws Exception {
        String batchPath = started.getRedirectedUrl();
        String batchId = batchPath.substring(batchPath.lastIndexOf('/') + 1);
        while (!batches.progress(batchId).orElseThrow().finished()) {
            mockMvc.perform(post(batchPath + "/run").with(csrf()).with(articleEditor()));
        }
        return batches.progress(batchId).orElseThrow();
    }

    private boolean published(EntityData node) {
        return Boolean.TRUE.equals(nodes.find(((Number) node.id()).longValue()).orElseThrow().fields()
                .get(BaseFieldDefinition.STATUS));
    }

    @Test
    void theContentOverviewHasABoxOnEachRowAndAFormOfferingTheContentActions() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");

        Document page = overview();

        assertThat(page.select("input[name=items]").eachAttr("value")).containsExactly(item(spring));
        assertThat(page.selectFirst("input[name=items]").attr("form")).isEqualTo(BulkFormField.FORM_ID);
        assertThat(page.selectFirst("input[name=items]").attr("aria-label")).isEqualTo("Select Spring schedule");
        assertThat(page.select("#views-bulk-form select[name=action] option").eachAttr("value")).containsExactly("",
                DeleteNode.ID, "node_make_sticky", "node_make_unsticky", "node_promote", PublishNode.ID,
                "node_unpromote", "node_save", "node_unpublish");
        assertThat(page.selectFirst("#views-bulk-form select[name=action]").hasAttr("required")).isTrue();
        assertThat(page.selectFirst("#views-bulk-form input[name=_csrf]")).isNotNull();
        assertThat(page.selectFirst("#views-bulk-form input[name=destination]").val()).isEqualTo(CONTENT_PATH);
        assertThat(page.select("script[type=module]").html()).contains("initBulkSelection");
        BootstrapAssertions.assertNoOutlineButtons(page);
    }

    @Test
    void theFormKeepsTheFiltersAsItsDestination() throws Exception {
        node(ARTICLE, "Spring schedule");

        Document page = Jsoup.parse(mockMvc.perform(get(CONTENT_PATH + "?title=Spring").with(articleEditor()))
                .andReturn().getResponse().getContentAsString());

        assertThat(page.selectFirst("#views-bulk-form input[name=destination]").val())
                .isEqualTo(CONTENT_PATH + "?title=Spring");
    }

    @Test
    void anEmptyListingHasNoBulkForm() throws Exception {
        assertThat(overview().select("#views-bulk-form")).isEmpty();
    }

    @Test
    void applyingAnActionChangesExactlyTheTickedRowsThroughABatch() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");
        EntityData summer = node(ARTICLE, "Summer schedule");
        EntityData autumn = node(ARTICLE, "Autumn schedule");

        MockHttpServletResponse started = apply(PublishNode.ID, List.of(item(spring), item(autumn)));

        assertThat(started.getRedirectedUrl()).startsWith("/batch/");
        BatchProgress progress = runBatch(started);
        assertThat(progress.errors()).isEmpty();
        assertThat(progress.total()).isEqualTo(2);
        assertThat(published(spring)).isTrue();
        assertThat(published(summer)).isFalse();
        assertThat(published(autumn)).isTrue();
        assertThat(batches.definition(started.getRedirectedUrl().substring("/batch/".length())).orElseThrow()
                .finishedPath()).isEqualTo(CONTENT_PATH + "?status=0");
    }

    @Test
    void aDestructiveActionAsksForConfirmationBeforeAnythingChanges() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");
        EntityData summer = node(ARTICLE, "Summer schedule");

        Document confirm = Jsoup.parse(apply(DeleteNode.ID, List.of(item(spring))).getContentAsString());

        assertThat(confirm.title()).isEqualTo("Apply Delete content to 1 item?");
        assertThat(confirm.select(".bulk-items li").eachText()).containsExactly("Spring schedule");
        assertThat(confirm.select("input[name=items]").eachAttr("value")).containsExactly(item(spring));
        assertThat(confirm.selectFirst("button.btn-danger").text()).isEqualTo("Delete content");
        assertThat(nodes.find(((Number) spring.id()).longValue())).isPresent();

        runBatch(apply(DeleteNode.ID, List.of(item(spring)), BulkForm.CONFIRMED, "true"));

        assertThat(nodes.find(((Number) spring.id()).longValue())).isEmpty();
        assertThat(nodes.find(((Number) summer.id()).longValue())).isPresent();
    }

    @Test
    void theConfirmationCountsSeveralItems() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");
        EntityData summer = node(ARTICLE, "Summer schedule");

        Document confirm = Jsoup.parse(apply(DeleteNode.ID, List.of(item(spring), item(summer)))
                .getContentAsString());

        assertThat(confirm.title()).isEqualTo("Apply Delete content to 2 items?");
    }

    @Test
    void anEntityThePersonMayNotChangeIsLeftAsItWasAndReported() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");
        EntityData about = node(PAGE, "About us");

        BatchProgress progress = runBatch(apply(PublishNode.ID, List.of(item(spring), item(about))));

        assertThat(published(spring)).isTrue();
        assertThat(published(about)).isFalse();
        assertThat(progress.errors()).containsExactly("action: You may not apply Publish content to About us.");
    }

    @Test
    void anEntityDeletedSinceItWasTickedIsReported() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");
        MockHttpServletResponse started = apply(PublishNode.ID, List.of(item(spring)));
        nodes.delete(((Number) spring.id()).longValue());

        assertThat(runBatch(started).errors())
                .containsExactly("action: The node " + spring.id() + " no longer exists.");
    }

    @Test
    void noActionOrNoSelectionIsRefusedWithoutChangingAnything() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");

        MockHttpServletResponse noAction = apply("", List.of(item(spring)));
        assertThat(noAction.getStatus()).isEqualTo(400);
        assertThat(Jsoup.parse(noAction.getContentAsString()).select(".bulk-error").eachText())
                .containsExactly(BulkForm.CHOOSE_ACTION);

        MockHttpServletResponse nothingTicked = apply(PublishNode.ID, List.of());
        assertThat(Jsoup.parse(nothingTicked.getContentAsString()).select(".bulk-error").eachText())
                .containsExactly(BulkForm.SELECT_ITEMS);
        assertThat(Jsoup.parse(nothingTicked.getContentAsString()).selectFirst("a.btn").attr("href"))
                .isEqualTo(CONTENT_PATH + "?status=0");

        assertThat(published(spring)).isFalse();
    }

    @Test
    void anActionTheListingDoesNotOfferOrItemsFromElsewhereAreRefused() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");

        assertThat(Jsoup.parse(apply("user_block", List.of(item(spring))).getContentAsString())
                .select(".bulk-error").eachText()).containsExactly(BulkForm.CHOOSE_ACTION);
        assertThat(Jsoup.parse(apply(PublishNode.ID, List.of("user:5", "node:x")).getContentAsString())
                .select(".bulk-error").eachText()).containsExactly("Choose items from the listing.");
        assertThat(Jsoup.parse(apply(PublishNode.ID, List.of("node", ":5")).getContentAsString())
                .select(".bulk-error").eachText()).containsExactly("Choose items from the listing.");
    }

    @Test
    void aDestinationOffTheSiteFallsBackToTheListing() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");

        MockHttpServletResponse started = mockMvc.perform(post(BULK_PATH).param(BulkForm.ACTION, PublishNode.ID)
                .param(BulkFormField.ITEMS, item(spring)).param(BulkForm.DESTINATION, "https://elsewhere.example/")
                .with(csrf()).with(articleEditor())).andReturn().getResponse();

        String batchId = started.getRedirectedUrl().substring("/batch/".length());
        assertThat(batches.definition(batchId).orElseThrow().finishedPath()).isEqualTo(CONTENT_PATH);
    }

    @Test
    void aProtocolRelativeDestinationFallsBackToTheListing() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");

        MockHttpServletResponse started = mockMvc.perform(post(BULK_PATH).param(BulkForm.ACTION, PublishNode.ID)
                .param(BulkFormField.ITEMS, item(spring)).param(BulkForm.DESTINATION, "//elsewhere.example/")
                .with(csrf()).with(articleEditor())).andReturn().getResponse();

        String batchId = started.getRedirectedUrl().substring("/batch/".length());
        assertThat(batches.definition(batchId).orElseThrow().finishedPath()).isEqualTo(CONTENT_PATH);
    }

    @Test
    void theBulkFormLeavesTheTokenOutWhenTheRequestHasNone() {
        String markup = bulkForm.render(DefaultViews.CONTENT, "page_1", List.of(), CONTENT_PATH,
                new MockHttpServletRequest());

        assertThat(Jsoup.parse(markup).select("input[name=_csrf]")).isEmpty();
    }

    @Test
    void aListingWithoutABulkFormOrAnUnknownOneIsNotFound() throws Exception {
        assertThat(mockMvc.perform(post(BulkForm.path(DefaultViews.FRONTPAGE, "feed_1")).with(csrf())
                .with(articleEditor())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(post(BulkForm.path("missing", "page_1")).with(csrf())
                .with(articleEditor())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(mockMvc.perform(post(BulkForm.path(DefaultViews.CONTENT, "page_9")).with(csrf())
                .with(articleEditor())).andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void someoneWhoMayNotSeeTheListingIsRefused() throws Exception {
        EntityData spring = node(ARTICLE, "Spring schedule");

        assertThat(mockMvc.perform(post(BULK_PATH).param(BulkForm.ACTION, PublishNode.ID)
                .param(BulkFormField.ITEMS, item(spring)).with(csrf()).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(published(spring)).isFalse();
    }
}
