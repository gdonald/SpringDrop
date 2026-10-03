package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityRevision;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class NodeRevisionIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUBTITLE = "subtitle";

    private static final String TAGS = "tags";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private long authorId;

    private long nodeId;

    @BeforeEach
    void anArticleWrittenByItsAuthor() {
        menus.install();
        storedLinks.install();
        accounts.install();
        clearContent();
        authorId = accounts.create("edith", "edith@example.com", "").id();
        types.save(NodeType.of(ARTICLE, "Article"));
        fields.createStorage(new FieldStorageConfig(SUBTITLE, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, ARTICLE, "Subtitle"));
        fields.createStorage(FieldStorageConfig.multiple(TAGS, NodeEntityType.ID, StringFieldType.ID,
                FieldStorageConfig.UNLIMITED));
        fields.createInstance(FieldInstanceConfig.of(TAGS, NodeEntityType.ID, ARTICLE, "Tags"));

        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), authorId)
                .fields());
        values.put(SUBTITLE, "Office opens at nine");
        nodeId = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule", values),
                authorId, true, "First draft.").id()).longValue();
    }

    @AfterEach
    void noContentLeft() {
        clearContent();
    }

    private void clearContent() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        List.of(SUBTITLE, TAGS).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        types.all().forEach(type -> types.delete(type.id()));
        accounts.findByName("edith").ifPresent(account -> entities.delete("user", account.id()));
    }

    private RequestPostProcessor author(String... extra) {
        List<String> granted = new ArrayList<>(List.of(NodePermissions.ACCESS_CONTENT,
                NodePermissions.editOwn(ARTICLE), NodePermissions.deleteOwn(ARTICLE)));
        granted.addAll(List.of(extra));
        return user(new AccountPrincipal(authorId, "edith", "", true, granted));
    }

    private RequestPostProcessor reviewer() {
        return author(NodePermissions.viewRevisions(ARTICLE), NodePermissions.revertRevisions(ARTICLE),
                NodePermissions.deleteRevisions(ARTICLE));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void redirects(MockHttpServletRequestBuilder request, String to) throws Exception {
        mockMvc.perform(request.with(reviewer()).with(csrf())).andExpect(redirectedUrl(to));
    }

    private void edit(String title, String subtitle, boolean newRevision, String log) throws Exception {
        MockHttpServletRequestBuilder request = post(NodeEntityType.editPath(nodeId))
                .param(NodeController.TITLE, title)
                .param(SUBTITLE, subtitle)
                .param(NodeController.REVISION_LOG, log);
        if (newRevision) {
            request.param(NodeController.NEW_REVISION, "on");
        }
        mockMvc.perform(request.with(author()).with(csrf())).andExpect(status().is3xxRedirection());
    }

    private List<EntityRevision> revisions() {
        return entities.revisions(NodeEntityType.ID, nodeId);
    }

    private long revision(int newestFirst) {
        return revisions().get(newestFirst).revisionId();
    }

    private String history() {
        return NodeRevisionController.historyPath(nodeId);
    }

    @Test
    void anEditMakesARevisionTheHistoryListsAndRevertingRestoresTheOldOneAsANewOne() throws Exception {
        edit("Summer schedule", "Office opens at ten today", true, "New hours.");

        Document listed = page(history(), reviewer());
        assertThat(listed.select("tbody tr")).hasSize(2);
        assertThat(listed.selectFirst("tbody tr").text()).contains("by edith", "New hours.", "Current revision");

        Document diff = page(history() + "/diff?left=" + revision(1) + "&right=" + revision(0), reviewer());
        assertThat(diff.select("tr[data-changed=true] th").eachText()).containsExactly("Title", "Subtitle");
        assertThat(diff.select("del").eachText()).containsExactly("Spring", "nine");
        assertThat(diff.select("ins").eachText()).containsExactly("Summer", "ten today");

        long original = revision(1);
        redirects(post(history() + "/" + original + "/revert"), history());

        assertThat(revisions()).hasSize(3);
        assertThat(nodes.find(nodeId)).hasValueSatisfying(node -> {
            assertThat(node.label()).isEqualTo("Spring schedule");
            assertThat(node.fields()).containsEntry(SUBTITLE, "Office opens at nine");
            assertThat(String.valueOf(node.fields().get(NodeEntityType.REVISION_LOG)))
                    .startsWith("Copy of the revision from ");
        });
    }

    @Nested
    class SavingRevisions {

        @Test
        void theEditFormStartsWithTheTypesRevisionChoiceAndAddingOffersNone() throws Exception {
            assertThat(page(NodeEntityType.editPath(nodeId), author())
                    .selectFirst("input[name=" + NodeController.NEW_REVISION + "]").hasAttr("checked")).isTrue();
            assertThat(page(NodeController.ADD_PATH + "/" + ARTICLE, author(NodePermissions.create(ARTICLE)))
                    .select("input[name=" + NodeController.NEW_REVISION + "]")).isEmpty();
        }

        @Test
        void anEditWithoutANewRevisionWritesOverTheCurrentOne() throws Exception {
            edit("Spring schedule", "Office opens at eight", false, "Fixed the hour.");

            assertThat(revisions()).hasSize(1);
            assertThat(revisions().getFirst().baseValues())
                    .containsEntry(NodeEntityType.REVISION_LOG, "Fixed the hour.");
        }

        @Test
        void aLogMessageTooLongIsNotSaved() throws Exception {
            mockMvc.perform(post(NodeEntityType.editPath(nodeId)).with(author()).with(csrf())
                            .param(NodeController.TITLE, "Spring schedule")
                            .param(NodeController.REVISION_LOG, "x".repeat(4001)))
                    .andExpect(status().isOk());

            assertThat(revisions()).hasSize(1);
        }
    }

    @Nested
    class ReadingRevisions {

        @Test
        void anEarlierRevisionIsShownAsItWas() throws Exception {
            edit("Summer schedule", "Office opens at ten", true, "");

            Document document = page(history() + "/" + revision(1) + "/view", reviewer());

            assertThat(document.selectFirst("h1").text()).startsWith("Revision of Spring schedule from ");
            assertThat(document.selectFirst(".node__content").text()).contains("Office opens at nine");
        }

        @Test
        void aNodeWithOneRevisionOffersNothingToCompare() throws Exception {
            Document document = page(history(), reviewer());

            assertThat(document.select("input[type=radio]")).isEmpty();
            assertThat(document.select("a:contains(Revert), a:contains(Delete)")).isEmpty();
        }

        @Test
        void revisionsComparedEitherWayRoundPutTheOlderOneFirst() throws Exception {
            edit("Summer schedule", "Office opens at nine", true, "");

            Document document = page(history() + "/diff?left=" + revision(0) + "&right=" + revision(1), reviewer());

            assertThat(document.select("del").eachText()).containsExactly("Spring");
        }

        @Test
        void aMultiValuedFieldIsComparedValueByValue() throws Exception {
            EntityData node = nodes.find(nodeId).orElseThrow();
            Map<String, Object> values = new LinkedHashMap<>(node.fields());
            values.put(TAGS, List.of("news", "hours"));
            nodes.save(node.withFields(values), authorId);

            Document document = page(history() + "/diff?left=" + revision(1) + "&right=" + revision(0), reviewer());

            assertThat(document.select("ins").eachText()).containsExactly("news hours");
        }

        @Test
        void aRevisionSavedWithoutItsDateOrAuthorSaysSo() throws Exception {
            EntityData node = nodes.find(nodeId).orElseThrow();
            Map<String, Object> values = new LinkedHashMap<>(node.fields());
            values.remove(NodeEntityType.REVISION_CREATED);
            values.remove(NodeEntityType.REVISION_USER);
            entities.save(node.withFields(values));

            assertThat(page(history(), reviewer()).selectFirst("tbody tr").text())
                    .contains("an unknown date", "by " + UserAccount.ANONYMOUS_NAME);
        }

        @Test
        void aRevisionByAnAccountThatIsGoneIsByAnonymous() throws Exception {
            nodes.save(nodes.find(nodeId).orElseThrow(), 999_999L);

            assertThat(page(history(), reviewer()).selectFirst("tbody tr").text())
                    .contains("by " + UserAccount.ANONYMOUS_NAME);
        }

        @Test
        void aRevisionTheNodeNeverHadIsNotFound() throws Exception {
            mockMvc.perform(get(history() + "/999999/view").with(reviewer())).andExpect(status().isNotFound());
            mockMvc.perform(get(NodeRevisionController.historyPath(999_999)).with(reviewer()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class RevertingAndDeleting {

        @BeforeEach
        void aSecondRevision() throws Exception {
            edit("Summer schedule", "Office opens at ten", true, "");
        }

        @Test
        void revertingAsksFirst() throws Exception {
            assertThat(page(history() + "/" + revision(1) + "/revert", reviewer()).select("button[type=submit]")
                    .text()).isEqualTo("Revert");
        }

        @Test
        void anEarlierRevisionIsDeletedOnceConfirmed() throws Exception {
            long earlier = revision(1);
            assertThat(page(history() + "/" + earlier + "/delete", reviewer()).select("button[type=submit]")
                    .text()).isEqualTo("Delete revision");

            redirects(post(history() + "/" + earlier + "/delete"), history());

            assertThat(revisions()).extracting(EntityRevision::revisionId).doesNotContain(earlier);
        }

        @Test
        void theCurrentRevisionCannotBeRevertedToOrDeleted() throws Exception {
            long current = revision(0);

            mockMvc.perform(get(history() + "/" + current + "/revert").with(reviewer()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(post(history() + "/" + current + "/delete").with(reviewer()).with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void eachEarlierRevisionOffersWhatThePersonMayDo() throws Exception {
            Document document = page(history(), reviewer());

            assertThat(document.select("tbody tr").get(1).select("a.btn").eachText())
                    .containsExactly("Revert", "Delete");
        }
    }

    @Nested
    class WhoMay {

        @Test
        void someoneWithoutARevisionPermissionCannotReadTheHistoryOrSeeItsTab() throws Exception {
            mockMvc.perform(get(history()).with(author())).andExpect(status().isForbidden());
            assertThat(page(NodeEntityType.path(nodeId), author()).select("a[href=" + history() + "]")).isEmpty();
            assertThat(page(NodeEntityType.path(nodeId), reviewer()).select("a[href=" + history() + "]"))
                    .isNotEmpty();
        }

        @Test
        void readingEveryTypesRevisionsOpensThisOnesButNotRevertingOrDeleting() throws Exception {
            edit("Summer schedule", "Office opens at ten", true, "");
            RequestPostProcessor reader = author(NodePermissions.VIEW_ALL_REVISIONS);

            assertThat(page(history(), reader).select("tbody a.btn")).isEmpty();
            mockMvc.perform(post(history() + "/" + revision(1) + "/revert").with(reader).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(history() + "/" + revision(1) + "/delete").with(reader).with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void bypassingNodeAccessOrBeingTheFirstAccountOpensEverything() throws Exception {
            edit("Summer schedule", "Office opens at ten", true, "");
            RequestPostProcessor bypass = user(new AccountPrincipal(8L, "admin", "", true,
                    List.of(NodePermissions.BYPASS_NODE_ACCESS)));
            RequestPostProcessor first = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "root", "", true,
                    List.of()));

            assertThat(page(history(), bypass).select("tbody a.btn").eachText()).containsExactly("Revert", "Delete");
            assertThat(page(history(), first).select("tbody a.btn").eachText()).containsExactly("Revert", "Delete");
        }

        @Test
        void revertingAndDeletingAlsoTakeBeingAllowedToEditAndDeleteTheNode() throws Exception {
            edit("Summer schedule", "Office opens at ten", true, "");
            RequestPostProcessor someoneElse = user(new AccountPrincipal(8L, "other", "", true, List.of(
                    NodePermissions.ACCESS_CONTENT, NodePermissions.VIEW_ALL_REVISIONS,
                    NodePermissions.REVERT_ALL_REVISIONS, NodePermissions.DELETE_ALL_REVISIONS)));

            assertThat(page(history(), someoneElse).select("tbody a.btn")).isEmpty();
        }

        @Test
        void revisionPermissionsDoNotOpenANodeThePersonMayNotRead() throws Exception {
            RequestPostProcessor outsider = user(new AccountPrincipal(8L, "other", "", true,
                    List.of(NodePermissions.VIEW_ALL_REVISIONS)));

            mockMvc.perform(get(history()).with(outsider)).andExpect(status().isForbidden());
        }
    }
}
