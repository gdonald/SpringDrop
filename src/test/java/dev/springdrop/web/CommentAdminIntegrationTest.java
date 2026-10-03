package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.comment.CommentEntityType;
import dev.springdrop.kernel.comment.CommentFieldType;
import dev.springdrop.kernel.comment.CommentFormatter;
import dev.springdrop.kernel.comment.CommentPermissions;
import dev.springdrop.kernel.comment.CommentService;
import dev.springdrop.kernel.comment.CommentStatus;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountPrincipal;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class CommentAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String FIELD = "field_comments";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private CommentService comments;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private RoleManager roles;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private long node;

    @BeforeEach
    void anArticleThatAnonymousVisitorsMayCommentOn() {
        menus.install();
        storedLinks.install();
        roles.install();
        clear();
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(FieldStorageConfig.single(FIELD, NodeEntityType.ID, CommentFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of(FIELD, NodeEntityType.ID, ARTICLE, "Comments"));
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
        values.put(FIELD, CommentStatus.OPEN.value());
        node = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule", values), 7L)
                .id()).longValue();
        roles.grant(RoleConfig.ANONYMOUS, NodePermissions.ACCESS_CONTENT);
        roles.grant(RoleConfig.ANONYMOUS, CommentPermissions.ACCESS_COMMENTS);
        roles.grant(RoleConfig.ANONYMOUS, CommentPermissions.POST_COMMENTS);
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(CommentEntityType.ID).ids().forEach(id -> entities.delete(CommentEntityType.ID, id));
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        fields.findStorage(NodeEntityType.ID, FIELD)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, FIELD));
        types.all().forEach(type -> types.delete(type.id()));
        roles.find(RoleConfig.ANONYMOUS).ifPresent(anonymous -> anonymous.permissions()
                .forEach(permission -> roles.revoke(RoleConfig.ANONYMOUS, permission)));
    }

    private static RequestPostProcessor administrator() {
        return user(new AccountPrincipal(9L, "admin", "", true, List.of(NodePermissions.ACCESS_CONTENT,
                CommentPermissions.ACCESS_COMMENTS, CommentPermissions.POST_COMMENTS,
                CommentPermissions.ADMINISTER_COMMENTS)));
    }

    private String replyPath() {
        return CommentFormatter.replyPath(NodeEntityType.ID, node, FIELD);
    }

    private long postedAnonymously(String path, String subject) throws Exception {
        String redirect = mockMvc.perform(post(path).with(csrf())
                        .param(CommentController.SUBJECT, subject)
                        .param(CommentController.BODY, subject + " text."))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        return Long.parseLong(redirect.substring(redirect.indexOf("#comment-") + "#comment-".length()));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document anonymousNodePage() throws Exception {
        return Jsoup.parse(mockMvc.perform(get(NodeEntityType.path(node)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void applies(String path, String operation, long... ids) throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(administrator()).with(csrf())
                .param(CommentAdminController.OPERATION, operation);
        for (long id : ids) {
            request.param(CommentAdminController.ROW_PREFIX + id, "true");
        }
        mockMvc.perform(request).andExpect(redirectedUrl(path));
    }

    @Test
    void anAnonymousCommentWaitsInTheQueueHiddenUntilApprovedAndRepliesSortUnderIt() throws Exception {
        long question = postedAnonymously(replyPath(), "Question");
        assertThat(anonymousNodePage().select(".comment")).isEmpty();
        assertThat(page(CommentAdminController.APPROVAL_PATH, administrator()).select("tbody tr")
                .eachAttr("data-comment")).containsExactly(String.valueOf(question));
        mockMvc.perform(get(replyPath() + "/" + question)).andExpect(status().isNotFound());

        applies(CommentAdminController.APPROVAL_PATH, CommentAdminController.APPROVE, question);
        long second = postedAnonymously(replyPath(), "Second");
        long answer = postedAnonymously(replyPath() + "/" + question, "Answer");
        applies(CommentAdminController.APPROVAL_PATH, CommentAdminController.APPROVE, second, answer);

        assertThat(anonymousNodePage().select(".comment").eachAttr("data-comment")).containsExactly(
                String.valueOf(question), String.valueOf(answer), String.valueOf(second));
        assertThat(comments.unapproved()).isEmpty();
    }

    @Test
    void theAdminIsClosedToSomeoneWhoMayNotAdministerComments() throws Exception {
        mockMvc.perform(get(CommentAdminController.PATH).with(user("visitor"))).andExpect(status().isForbidden());
        mockMvc.perform(get(CommentAdminController.APPROVAL_PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void eachListShowsItsCommentsWithWhatTheyAreAboutAndEditAsAButton() throws Exception {
        long id = postedAnonymously(replyPath(), "Question");
        comments.approve(id);

        Document document = page(CommentAdminController.PATH, administrator());

        assertThat(document.selectFirst("tr[data-comment=" + id + "]").text())
                .contains("Question", "Anonymous", "Spring schedule");
        assertThat(document.selectFirst("tr[data-comment=" + id + "] a[href=" + NodeEntityType.path(node) + "]"))
                .isNotNull();
        assertThat(document.select(".nav-link.active").text()).isEqualTo("Published comments");
        BootstrapAssertions.assertNoOutlineButtons(document);
        BootstrapAssertions.assertEditControlsAreButtons(document);
    }

    @Test
    void anEmptyListSaysSo() throws Exception {
        assertThat(page(CommentAdminController.APPROVAL_PATH, administrator()).text())
                .contains("There are no comments here.");
    }

    @Test
    void publishedCommentsAreUnpublishedInBulk() throws Exception {
        long id = postedAnonymously(replyPath(), "Question");
        comments.approve(id);

        applies(CommentAdminController.PATH, CommentAdminController.UNPUBLISH, id);

        assertThat(comments.find(id)).map(CommentService::published).contains(false);
    }

    @Test
    void deletingInBulkAsksFirstAndThenDeletesWithReplies() throws Exception {
        long question = postedAnonymously(replyPath(), "Question");
        comments.approve(question);
        long answer = postedAnonymously(replyPath() + "/" + question, "Answer");

        Document confirm = Jsoup.parse(mockMvc.perform(post(CommentAdminController.PATH)
                        .with(administrator()).with(csrf())
                        .param(CommentAdminController.OPERATION, CommentAdminController.DELETE)
                        .param(CommentAdminController.ROW_PREFIX + question, "true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(confirm.select("li").eachText()).containsExactly("Question");
        assertThat(comments.find(question)).isPresent();

        mockMvc.perform(post(CommentAdminController.PATH).with(administrator()).with(csrf())
                        .param(CommentAdminController.OPERATION, CommentAdminController.DELETE)
                        .param(CommentAdminController.CONFIRMED, "true")
                        .param(CommentAdminController.ROW_PREFIX + question, "true"))
                .andExpect(redirectedUrl(CommentAdminController.PATH));

        assertThat(comments.find(question)).isEmpty();
        assertThat(comments.find(answer)).isEmpty();
    }

    @Test
    void nothingTickedAnActionTheListDoesNotOfferOrATickOutsideTheListChangesNothing() throws Exception {
        long waiting = postedAnonymously(replyPath(), "Waiting");
        long live = postedAnonymously(replyPath(), "Live");
        comments.approve(live);

        Document nothing = Jsoup.parse(mockMvc.perform(post(CommentAdminController.APPROVAL_PATH)
                        .with(administrator()).with(csrf())
                        .param(CommentAdminController.OPERATION, CommentAdminController.APPROVE))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Document wrongAction = Jsoup.parse(mockMvc.perform(post(CommentAdminController.PATH)
                        .with(administrator()).with(csrf())
                        .param(CommentAdminController.OPERATION, CommentAdminController.APPROVE)
                        .param(CommentAdminController.ROW_PREFIX + live, "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        applies(CommentAdminController.PATH, CommentAdminController.UNPUBLISH, live, waiting);

        assertThat(nothing.text()).contains(CommentAdminController.NOTHING_CHOSEN);
        assertThat(wrongAction.text()).contains(CommentAdminController.NOTHING_CHOSEN);
        assertThat(comments.find(waiting)).map(CommentService::published).contains(false);
        assertThat(comments.find(live)).map(CommentService::published).contains(false);
    }

    @Test
    void aCommentWhoseHostIsGoneListsWithNoTitleAndWhenChangedIsUnknown() throws Exception {
        Map<String, Object> values = new LinkedHashMap<>(CommentService.draft(NodeEntityType.ID, 999_999L, FIELD,
                CommentEntityType.NO_PARENT).fields());
        values.put(CommentEntityType.BODY, "Orphaned.");
        values.put(BaseFieldDefinition.STATUS, true);
        EntityData orphan = entities.save(EntityData.of(CommentEntityType.ID, null, null, "Orphan", values));

        Document document = page(CommentAdminController.PATH, administrator());

        assertThat(document.select("tr[data-comment=" + orphan.id() + "] td").eachText())
                .containsExactly("Orphan", "Anonymous", "Edit");
    }
}
