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
import dev.springdrop.kernel.comment.CommentSettings;
import dev.springdrop.kernel.comment.CommentStatus;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class CommentIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String FIELD = "field_comments";

    private static final long READER = 8L;

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
    private ViewDisplayManager viewDisplays;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private RoleManager roles;

    @Autowired
    private PluginRegistry pluginRegistry;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private long node;

    @BeforeEach
    void anArticleOpenForComments() {
        menus.install();
        storedLinks.install();
        accounts.install();
        roles.install();
        clear();
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(FieldStorageConfig.single(FIELD, NodeEntityType.ID, CommentFieldType.ID));
        settings(Map.of());
        node = article(CommentStatus.OPEN);
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
        accounts.findByName("edith").ifPresent(account -> entities.delete("user", account.id()));
        roles.find(RoleConfig.ANONYMOUS).ifPresent(anonymous -> anonymous.permissions()
                .forEach(permission -> roles.revoke(RoleConfig.ANONYMOUS, permission)));
    }

    private void settings(Map<String, Object> settings) {
        fields.createInstance(FieldInstanceConfig.of(FIELD, NodeEntityType.ID, ARTICLE, "Comments")
                .withSettings(settings));
    }

    private long article(CommentStatus status) {
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
        values.put(FIELD, status.value());
        return ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule", values), 7L)
                .id()).longValue();
    }

    private static RequestPostProcessor commenter(long id, String... extra) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                CommentPermissions.ACCESS_COMMENTS, CommentPermissions.POST_COMMENTS,
                CommentPermissions.SKIP_APPROVAL, CommentPermissions.EDIT_OWN));
        granted.addAll(List.of(extra));
        return user(new AccountPrincipal(id, "account" + id, "", true, granted));
    }

    private static RequestPostProcessor reader() {
        return user(new AccountPrincipal(READER, "reader", "", true,
                List.of(NodePermissions.ACCESS_CONTENT, CommentPermissions.ACCESS_COMMENTS)));
    }

    private static RequestPostProcessor administrator() {
        return commenter(9L, CommentPermissions.ADMINISTER_COMMENTS);
    }

    private String replyPath() {
        return CommentFormatter.replyPath(NodeEntityType.ID, node, FIELD);
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document form(MockHttpServletRequestBuilder request, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(who).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document nodePage(RequestPostProcessor who) throws Exception {
        return page(NodeEntityType.path(node), who);
    }

    @Test
    void aPermittedPersonPostsACommentThatRendersUnderTheNode() throws Exception {
        long id = posted(replyPath(), "Thanks", "The new hours suit me.\nSee you in May.", commenter(7L));

        Document page = nodePage(reader());

        assertThat(page.selectFirst("#comment-" + id + " h3").text()).isEqualTo("Thanks");
        assertThat(page.selectFirst("#comment-" + id + " .comment__body").html().replace("\n", ""))
                .contains("suit me.<br>See you");
        assertThat(page.selectFirst("#comment-" + id + " .comment__author").text()).isEqualTo("Anonymous");
        assertThat(comments.find(id)).hasValueSatisfying(comment -> assertThat(comment.fields())
                .containsEntry(BaseFieldDefinition.STATUS, true)
                .containsEntry(CommentEntityType.HOSTNAME, "127.0.0.1"));
    }

    @Nested
    class Posting {

        @Test
        void theNodeOffersToAddACommentToSomeoneWhoMayPost() throws Exception {
            assertThat(nodePage(commenter(7L)).selectFirst("a:contains(Add new comment)").attr("href"))
                    .isEqualTo(replyPath());
            assertThat(nodePage(reader()).select("a:contains(Add new comment)")).isEmpty();
            BootstrapAssertions.assertNoOutlineButtons(nodePage(commenter(7L)));
        }

        @Test
        void aCommentWithoutASubjectIsGivenTheStartOfItsText() throws Exception {
            long id = posted(replyPath(), "", "The opening hours change in May, so plan ahead.", commenter(7L));

            assertThat(comments.find(id)).map(EntityData::label).contains("The opening hours change in");
        }

        @Test
        void aCommentWithoutTextIsNotPosted() throws Exception {
            Document document = form(post(replyPath()).param(CommentController.BODY, ""), commenter(7L));

            assertThat(document.selectFirst("textarea[name=" + CommentController.BODY + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(comments.published()).isEmpty();
        }

        @Test
        void someoneWhoMayNotPostIsRefused() throws Exception {
            mockMvc.perform(get(replyPath()).with(reader())).andExpect(status().isForbidden());
        }

        @Test
        void commentsOnSomethingThePersonMayNotReadAreRefused() throws Exception {
            EntityData unpublished = nodes.find(node).orElseThrow();
            Map<String, Object> values = new LinkedHashMap<>(unpublished.fields());
            values.put(BaseFieldDefinition.STATUS, false);
            nodes.save(unpublished.withFields(values), 7L);

            mockMvc.perform(get(replyPath()).with(commenter(READER))).andExpect(status().isForbidden());
        }

        @Test
        void someoneWhoMayPostButNotReadCommentsSeesNoneButMayAddOne() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));
            RequestPostProcessor writerOnly = user(new AccountPrincipal(READER, "writer", "", true,
                    List.of(NodePermissions.ACCESS_CONTENT, CommentPermissions.POST_COMMENTS)));

            Document page = nodePage(writerOnly);

            assertThat(page.select("#comment-" + id)).isEmpty();
            assertThat(page.select("a:contains(Add new comment)")).isNotEmpty();
        }

        @Test
        void anAdministratorsCommentIsPublishedWithoutWaiting() throws Exception {
            RequestPostProcessor administratorOnly = user(new AccountPrincipal(9L, "admin", "", true,
                    List.of(NodePermissions.ACCESS_CONTENT, CommentPermissions.ADMINISTER_COMMENTS)));

            long id = posted(replyPath(), "Noted", "Thanks.", administratorOnly);

            assertThat(comments.find(id)).map(CommentService::published).contains(true);
        }

        @Test
        void closedCommentsAreShownButTakeNoMore() throws Exception {
            long id = posted(replyPath(), "Early", "Before closing.", commenter(7L));
            EntityData closed = nodes.find(node).orElseThrow();
            Map<String, Object> values = new LinkedHashMap<>(closed.fields());
            values.put(FIELD, CommentStatus.CLOSED.value());
            nodes.save(closed.withFields(values), 7L);

            Document page = nodePage(commenter(7L));

            assertThat(page.select("#comment-" + id)).isNotEmpty();
            assertThat(page.select("a:contains(Add new comment), a:contains(Reply)")).isEmpty();
            mockMvc.perform(get(replyPath()).with(commenter(7L))).andExpect(status().isForbidden());
        }

        @Test
        void hiddenCommentsAreNeitherShownNorTaken() throws Exception {
            long hidden = article(CommentStatus.HIDDEN);

            assertThat(page(NodeEntityType.path(hidden), commenter(7L)).select(".comments")).isEmpty();
            mockMvc.perform(get(CommentFormatter.replyPath(NodeEntityType.ID, hidden, FIELD)).with(commenter(7L)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void commentsOnSomethingThatDoesNotTakeThemAreNotFound() throws Exception {
            fields.createStorage(FieldStorageConfig.single("field_subtitle", NodeEntityType.ID, StringFieldType.ID));
            fields.createInstance(FieldInstanceConfig.of("field_subtitle", NodeEntityType.ID, ARTICLE, "Subtitle"));
            try {
                mockMvc.perform(get(CommentFormatter.replyPath(NodeEntityType.ID, node, "field_subtitle"))
                        .with(commenter(7L))).andExpect(status().isNotFound());
                mockMvc.perform(get(CommentFormatter.replyPath(NodeEntityType.ID, node, "field_missing"))
                        .with(commenter(7L))).andExpect(status().isNotFound());
                mockMvc.perform(get(CommentFormatter.replyPath(NodeEntityType.ID, 999_999, FIELD))
                        .with(commenter(7L))).andExpect(status().isNotFound());
                mockMvc.perform(get(CommentFormatter.replyPath("gadget", node, FIELD))
                        .with(commenter(7L))).andExpect(status().isNotFound());
            } finally {
                fields.deleteStorage(NodeEntityType.ID, "field_subtitle");
            }
        }

        @Test
        void aFieldDrawnWithoutItsEntityShowsNoComments() {
            posted(replyPath(), "Thanks", "Noted.", commenter(7L));

            assertThat(viewDisplays.render(NodeEntityType.ID, ARTICLE, ViewDisplayConfig.FULL_MODE,
                    nodes.find(node).orElseThrow().fields())).doesNotContain("comments");
        }
    }

    @Nested
    class Preview {

        @Test
        void anOptionalPreviewShowsTheCommentAboveTheFormWithSaveStillOffered() throws Exception {
            Document document = form(post(replyPath())
                    .param(CommentController.SUBJECT, "Thanks")
                    .param(CommentController.BODY, "Noted.")
                    .param(CommentController.PREVIEW, ""), commenter(7L));

            assertThat(document.selectFirst(".comment-preview").text()).contains("Thanks", "Noted.");
            assertThat(document.select("button[name=save]")).isNotEmpty();
            assertThat(comments.published()).isEmpty();
        }

        @Test
        void aRequiredPreviewOffersSaveOnlyOnceTheCommentWasPreviewed() throws Exception {
            settings(Map.of(CommentSettings.PREVIEW, CommentSettings.PREVIEW_REQUIRED));

            assertThat(page(replyPath(), commenter(7L)).select("button[name=save]")).isEmpty();
            Document refused = form(post(replyPath()).param(CommentController.BODY, "Noted."), commenter(7L));
            assertThat(refused.text()).contains("Preview the comment before saving it.");

            Document previewed = form(post(replyPath()).param(CommentController.BODY, "Noted.")
                    .param(CommentController.PREVIEW, ""), commenter(7L));
            assertThat(previewed.select("button[name=save]")).isNotEmpty();
            mockMvc.perform(post(replyPath()).with(commenter(7L)).with(csrf())
                            .param(CommentController.BODY, "Noted.")
                            .param(CommentController.PREVIEWED, "true"))
                    .andExpect(status().is3xxRedirection());
            assertThat(comments.published()).hasSize(1);
        }

        @Test
        void aFieldWithoutPreviewOffersNone() throws Exception {
            settings(Map.of(CommentSettings.PREVIEW, CommentSettings.PREVIEW_NONE));

            assertThat(page(replyPath(), commenter(7L)).select("button[name=" + CommentController.PREVIEW + "]"))
                    .isEmpty();
        }
    }

    @Nested
    class SomeoneNotSignedIn {

        @BeforeEach
        void anonymousVisitorsMayPost() {
            roles.grant(RoleConfig.ANONYMOUS, NodePermissions.ACCESS_CONTENT);
            roles.grant(RoleConfig.ANONYMOUS, CommentPermissions.POST_COMMENTS);
        }

        @Test
        void aFieldAskingForNoContactDetailsShowsNoneAndTheCommentIsByAnonymous() throws Exception {
            Document document = Jsoup.parse(mockMvc.perform(get(replyPath()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(document.select("input[name=" + CommentController.NAME + "]")).isEmpty();

            String redirect = mockMvc.perform(post(replyPath()).with(csrf()).param(CommentController.BODY, "Hi."))
                    .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
            long id = Long.parseLong(redirect.substring(redirect.indexOf("#comment-") + "#comment-".length()));

            assertThat(nodePage(administrator()).selectFirst("#comment-" + id + " .comment__author").text())
                    .isEqualTo("Anonymous");
        }

        @Test
        void requiredContactDetailsAreAskedForAndChecked() throws Exception {
            settings(Map.of(CommentSettings.ANONYMOUS, CommentSettings.CONTACT_REQUIRED));
            accounts.create("edith", "edith@example.com", "");

            Document document = Jsoup.parse(mockMvc.perform(post(replyPath()).with(csrf())
                            .param(CommentController.NAME, "edith")
                            .param(CommentController.MAIL, "not an address")
                            .param(CommentController.HOMEPAGE, "example.com")
                            .param(CommentController.BODY, "Hello."))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

            assertThat(document.select(".is-invalid").eachAttr("name")).containsExactlyInAnyOrder(
                    CommentController.NAME, CommentController.MAIL, CommentController.HOMEPAGE);
            assertThat(document.text()).contains("The name edith belongs to a registered account.");
        }

        @Test
        void anAnonymousCommentWaitsForApprovalWithTheDetailsLeft() throws Exception {
            settings(Map.of(CommentSettings.ANONYMOUS, CommentSettings.CONTACT_OPTIONAL));

            String redirect = mockMvc.perform(post(replyPath()).with(csrf())
                            .param(CommentController.NAME, "Visitor")
                            .param(CommentController.MAIL, "visitor@example.com")
                            .param(CommentController.HOMEPAGE, "https://example.com")
                            .param(CommentController.BODY, "Hello."))
                    .andExpect(status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();
            long id = Long.parseLong(redirect.substring(redirect.indexOf("#comment-") + "#comment-".length()));

            assertThat(comments.find(id)).hasValueSatisfying(comment -> assertThat(comment.fields())
                    .containsEntry(BaseFieldDefinition.STATUS, false)
                    .containsEntry(CommentEntityType.NAME, "Visitor")
                    .containsEntry(CommentEntityType.MAIL, "visitor@example.com"));
            assertThat(nodePage(reader()).select("#comment-" + id)).isEmpty();
            Document asAdministrator = nodePage(administrator());
            assertThat(asAdministrator.selectFirst("#comment-" + id).text())
                    .contains("Visitor (not verified)", "Unapproved");
        }
    }

    @Nested
    class Threads {

        @Test
        void repliesSortUnderWhatTheyReplyToAndAreIndented() throws Exception {
            long first = posted(replyPath(), "First", "One.", commenter(7L));
            long second = posted(replyPath(), "Second", "Two.", commenter(7L));
            long answer = posted(replyPath() + "/" + first, "Answer", "To one.", commenter(7L));
            long deeper = posted(replyPath() + "/" + answer, "Deeper", "To the answer.", commenter(7L));

            Document page = nodePage(reader());

            assertThat(page.select(".comment").eachAttr("data-comment")).containsExactly(
                    String.valueOf(first), String.valueOf(answer), String.valueOf(deeper), String.valueOf(second));
            assertThat(page.select(".comment").eachAttr("data-depth")).containsExactly("0", "1", "2", "0");
            assertThat(CommentService.threadOf(comments.find(deeper).orElseThrow())).isEqualTo("00.00.00/");
        }

        @Test
        void aFlatFieldListsCommentsInTheOrderPosted() throws Exception {
            settings(Map.of(CommentSettings.DEFAULT_MODE, CommentSettings.FLAT));
            long first = posted(replyPath(), "First", "One.", commenter(7L));
            long second = posted(replyPath(), "Second", "Two.", commenter(7L));

            Document page = nodePage(commenter(7L));

            assertThat(page.select(".comment").eachAttr("data-depth")).containsExactly("0", "0");
            assertThat(page.select(".comment").eachAttr("data-comment"))
                    .containsExactly(String.valueOf(first), String.valueOf(second));
            assertThat(page.select("a:contains(Reply)")).isEmpty();
        }

        @Test
        void aThreadStopsTakingRepliesAtItsDepth() throws Exception {
            settings(Map.of(CommentSettings.DEPTH, 2));
            long first = posted(replyPath(), "First", "One.", commenter(7L));
            long answer = posted(replyPath() + "/" + first, "Answer", "To one.", commenter(7L));

            assertThat(nodePage(commenter(7L)).select("#comment-" + answer + " a:contains(Reply)")).isEmpty();
            mockMvc.perform(get(replyPath() + "/" + answer).with(commenter(7L))).andExpect(status().isForbidden());
        }

        @Test
        void aReplyToACommentOnSomethingElseIsNotFound() throws Exception {
            long other = article(CommentStatus.OPEN);
            long elsewhere = posted(CommentFormatter.replyPath(NodeEntityType.ID, other, FIELD), "Elsewhere",
                    "Not here.", commenter(7L));

            mockMvc.perform(get(replyPath() + "/" + elsewhere).with(commenter(7L)))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(replyPath() + "/999999").with(commenter(7L))).andExpect(status().isNotFound());
        }

        @Test
        void theReplyFormSaysWhatIsBeingRepliedTo() throws Exception {
            long first = posted(replyPath(), "First", "One.", commenter(7L));

            assertThat(page(replyPath() + "/" + first, commenter(7L)).selectFirst("h1").text())
                    .isEqualTo("Reply to a comment");
        }
    }

    @Nested
    class ChangingComments {

        @Test
        void anAuthorEditsTheirOwnComment() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));
            assertThat(nodePage(commenter(7L)).select("#comment-" + id + " a:contains(Edit)")).isNotEmpty();
            assertThat(page(CommentEntityType.path(id) + "/edit", commenter(7L))
                    .selectFirst("textarea[name=" + CommentController.BODY + "]").text()).isEqualTo("Noted.");

            mockMvc.perform(post(CommentEntityType.path(id) + "/edit").with(commenter(7L)).with(csrf())
                            .param(CommentController.SUBJECT, "Thanks again")
                            .param(CommentController.BODY, "Still noted."))
                    .andExpect(redirectedUrl(CommentEntityType.path(id)));

            assertThat(comments.find(id)).hasValueSatisfying(comment -> {
                assertThat(comment.label()).isEqualTo("Thanks again");
                assertThat(CommentService.textOf(comment)).isEqualTo("Still noted.");
            });
        }

        @Test
        void anEditWithoutTextIsNotSaved() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));

            form(post(CommentEntityType.path(id) + "/edit").param(CommentController.BODY, ""), commenter(7L));

            assertThat(comments.find(id)).map(CommentService::textOf).contains("Noted.");
        }

        @Test
        void someoneElsesCommentCannotBeEdited() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));

            mockMvc.perform(get(CommentEntityType.path(id) + "/edit").with(commenter(READER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void anAdministratorUnpublishesAComment() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));
            assertThat(page(CommentEntityType.path(id) + "/edit", administrator())
                    .selectFirst("input[name=" + CommentController.PUBLISHED + "]").hasAttr("checked")).isTrue();

            mockMvc.perform(post(CommentEntityType.path(id) + "/edit").with(administrator()).with(csrf())
                    .param(CommentController.SUBJECT, "")
                    .param(CommentController.BODY, "Noted, with thanks."));

            assertThat(comments.find(id)).hasValueSatisfying(comment -> {
                assertThat(CommentService.published(comment)).isFalse();
                assertThat(comment.label()).isEqualTo("Noted, with thanks.");
            });
        }

        @Test
        void anAdministratorDeletesACommentWithItsReplies() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));
            long answer = posted(replyPath() + "/" + id, "Welcome", "Any time.", commenter(7L));
            assertThat(page(CommentEntityType.path(id) + "/delete", administrator()).select("button[type=submit]")
                    .text()).isEqualTo("Delete comment");

            mockMvc.perform(post(CommentEntityType.path(id) + "/delete").with(administrator()).with(csrf()))
                    .andExpect(redirectedUrl(NodeEntityType.path(node)));

            assertThat(comments.find(id)).isEmpty();
            assertThat(comments.find(answer)).isEmpty();
        }

        @Test
        void aCommentsAddressLeadsToItsPlaceOnThePage() throws Exception {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));

            mockMvc.perform(get(CommentEntityType.path(id)).with(reader()))
                    .andExpect(redirectedUrl(NodeEntityType.path(node) + "#comment-" + id));
            mockMvc.perform(get(CommentEntityType.path(999_999)).with(reader())).andExpect(status().isNotFound());
        }
    }

    @Nested
    class TheField {

        private RequestPostProcessor fieldAdministrator() {
            return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_FIELDS));
        }

        @Test
        void theNodeFormOffersOpenClosedAndHiddenStartingFromTheFieldsDefault() throws Exception {
            RequestPostProcessor writer = user(new AccountPrincipal(7L, "writer", "", true,
                    List.of(NodePermissions.create(ARTICLE), NodePermissions.editOwn(ARTICLE))));
            Document fresh = page(NodeController.ADD_PATH + "/" + ARTICLE, writer);
            assertThat(fresh.select("input[name=" + FIELD + "]").eachAttr("value")).containsExactly("0", "1", "2");
            assertThat(fresh.selectFirst("input[name=" + FIELD + "][checked]").val()).isEqualTo("2");

            fields.createInstance(FieldInstanceConfig.of(FIELD, NodeEntityType.ID, ARTICLE, "Comments")
                    .withDefaultValue("1"));
            assertThat(page(NodeController.ADD_PATH + "/" + ARTICLE, writer)
                    .selectFirst("input[name=" + FIELD + "][checked]").val()).isEqualTo("1");

            mockMvc.perform(post(NodeEntityType.editPath(node)).with(writer).with(csrf())
                    .param(NodeController.TITLE, "Spring schedule").param(FIELD, "1"));
            assertThat(nodes.find(node).orElseThrow().fields()).containsEntry(FIELD, 1);
            mockMvc.perform(post(NodeEntityType.editPath(node)).with(writer).with(csrf())
                    .param(NodeController.TITLE, "Spring schedule").param(FIELD, "7"));
            assertThat(nodes.find(node).orElseThrow().fields()).doesNotContainKey(FIELD);
            mockMvc.perform(post(NodeEntityType.editPath(node)).with(writer).with(csrf())
                    .param(NodeController.TITLE, "Spring schedule"));
            assertThat(nodes.find(node).orElseThrow().fields()).doesNotContainKey(FIELD);
        }

        @Test
        void theFieldsSettingsAreEditedOnTheFieldUiAndOtherSettingsKept() throws Exception {
            settings(Map.of("widget", "comment_default"));
            String path = FieldUiController.fieldsPath(NodeEntityType.ID, ARTICLE) + "/" + FIELD;
            assertThat(page(path, fieldAdministrator()).select("[name^=" + CommentFieldType.SETTINGS_PREFIX + "]"))
                    .hasSize(4);

            mockMvc.perform(post(path).with(fieldAdministrator()).with(csrf())
                    .param("label", "Comments")
                    .param(CommentFieldType.SETTINGS_PREFIX + CommentSettings.DEFAULT_MODE, CommentSettings.FLAT)
                    .param(CommentFieldType.SETTINGS_PREFIX + CommentSettings.ANONYMOUS, "2")
                    .param(CommentFieldType.SETTINGS_PREFIX + CommentSettings.PREVIEW, "9")
                    .param(CommentFieldType.SETTINGS_PREFIX + CommentSettings.DEPTH, "3"))
                    .andExpect(status().is3xxRedirection());

            assertThat(fields.findInstance(NodeEntityType.ID, ARTICLE, FIELD).orElseThrow().settings())
                    .containsEntry(CommentSettings.DEFAULT_MODE, CommentSettings.FLAT)
                    .containsEntry(CommentSettings.ANONYMOUS, 2)
                    .containsEntry(CommentSettings.PREVIEW, CommentSettings.PREVIEW_OPTIONAL)
                    .containsEntry(CommentSettings.DEPTH, 3)
                    .containsEntry("widget", "comment_default");
        }

        @Test
        void aDepthThatIsNotANumberIsRefusedOnBothSides() throws Exception {
            String path = FieldUiController.fieldsPath(NodeEntityType.ID, ARTICLE) + "/" + FIELD;

            Document document = form(post(path).param("label", "Comments")
                    .param(CommentFieldType.SETTINGS_PREFIX + CommentSettings.DEPTH, "deep"), fieldAdministrator());

            assertThat(document.selectFirst("input[name=" + CommentFieldType.SETTINGS_PREFIX + CommentSettings.DEPTH
                    + "]").hasClass("is-invalid")).isTrue();
            assertThat(new CommentFieldType().instanceSettingsValues(Map.of(
                    CommentFieldType.SETTINGS_PREFIX + CommentSettings.DEPTH, "deep"))).containsEntry(
                    CommentSettings.DEPTH, 0);
        }

        @Test
        void theThreadIsDrawnByTheCommentFormatter() {
            assertThat(pluginRegistry.managerFor(FieldFormatter.class).get(CommentFormatter.ID).id())
                    .isEqualTo(CommentFormatter.ID);
        }

        @Test
        void aCommentIsFieldableAndDrawnByItsTypesOwnName() {
            long id = posted(replyPath(), "Thanks", "Noted.", commenter(7L));

            assertThat(viewDisplays.render(comments.find(id).orElseThrow(), ViewDisplayConfig.FULL_MODE)).isEmpty();
        }
    }

    @Test
    void publishedAndUnapprovedCommentsAreListedApart() {
        EntityData waiting = comments.post(EntityData.of(CommentEntityType.ID, null, null, "Waiting",
                draftValues(false)));
        EntityData live = comments.post(EntityData.of(CommentEntityType.ID, null, null, "Live", draftValues(true)));

        assertThat(comments.unapproved()).extracting(EntityData::id).containsExactly(waiting.id());
        assertThat(comments.published()).extracting(EntityData::id).containsExactly(live.id());
        comments.approve(((Number) waiting.id()).longValue());
        assertThat(comments.unapproved()).isEmpty();
        comments.approve(999_999L);
    }

    @Test
    void aCommentWithNoAuthorOrDateSaysSo() throws Exception {
        EntityData bare = entities.save(EntityData.of(CommentEntityType.ID, null, null, "Bare", draftValues(true)));

        Document page = nodePage(reader());

        assertThat(page.selectFirst("#comment-" + bare.id() + " .comment__author").text()).isEqualTo("Anonymous");
        assertThat(page.selectFirst("#comment-" + bare.id()).text()).doesNotContain(" on ");
    }

    private Map<String, Object> draftValues(boolean published) {
        Map<String, Object> values = new LinkedHashMap<>(CommentService.draft(NodeEntityType.ID, node, FIELD,
                CommentEntityType.NO_PARENT).fields());
        values.put(CommentEntityType.BODY, "Text.");
        values.put(BaseFieldDefinition.STATUS, published);
        return values;
    }

    /** Posts a comment and answers with its id, which the redirect to its place on the page carries. */
    private long posted(String path, String subject, String body, RequestPostProcessor who) {
        try {
            String redirect = mockMvc.perform(post(path).with(who).with(csrf())
                            .param(CommentController.SUBJECT, subject)
                            .param(CommentController.BODY, body))
                    .andExpect(status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();
            return Long.parseLong(redirect.substring(redirect.indexOf("#comment-") + "#comment-".length()));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
