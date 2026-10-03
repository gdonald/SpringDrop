package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.kernel.workflow.WorkflowState;
import dev.springdrop.kernel.workflow.WorkflowTransition;
import dev.springdrop.kernel.workflow.types.ContentModerationWorkflowType;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
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
class WorkflowAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String EDITORIAL = WorkflowController.managePath("editorial");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkflowManager workflows;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void anEditorialWorkflow() {
        clear();
        workflows.save(workflows.create("editorial", "Editorial", ContentModerationWorkflowType.ID));
    }

    @AfterEach
    void noWorkflowsLeft() {
        clear();
    }

    private void clear() {
        configStore.listNames(WorkflowConfig.CONFIG_PREFIX).forEach(configStore::delete);
    }

    private static RequestPostProcessor workflowAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_WORKFLOWS));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(workflowAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document submit(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(workflowAdministrator()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void redirects(MockHttpServletRequestBuilder request, String to) throws Exception {
        mockMvc.perform(request.with(workflowAdministrator()).with(csrf())).andExpect(redirectedUrl(to));
    }

    private void notFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(workflowAdministrator()).with(csrf())).andExpect(status().isNotFound());
    }

    private WorkflowConfig editorial() {
        return workflows.find("editorial").orElseThrow();
    }

    @Test
    void theWorkflowsAreClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(WorkflowController.PATH).with(user("visitor"))).andExpect(status().isForbidden());
        mockMvc.perform(get(EDITORIAL).with(user("visitor"))).andExpect(status().isForbidden());
    }

    @Test
    void eachWorkflowIsListedWithItsTypeAndStatesAndActionsAsButtons() throws Exception {
        Document document = page(WorkflowController.PATH);

        assertThat(document.selectFirst("tbody tr").text()).contains("Editorial", "Content moderation",
                "Draft, Published");
        BootstrapAssertions.assertNoOutlineButtons(document);
        BootstrapAssertions.assertEditControlsAreButtons(document);
    }

    @Test
    void anEmptyListSaysSo() throws Exception {
        clear();

        assertThat(page(WorkflowController.PATH).text()).contains("There are no workflows yet.");
    }

    @Nested
    class AddingAndRemovingWorkflows {

        @Test
        void aWorkflowIsAddedWithTheStatesAndTransitionsItsTypeStartsWith() throws Exception {
            assertThat(page(WorkflowController.PATH + "/add").select("select[name=" + WorkflowController.TYPE
                    + "] option").eachAttr("value")).contains(ContentModerationWorkflowType.ID);

            redirects(post(WorkflowController.PATH + "/add")
                    .param(WorkflowController.LABEL, "Approval")
                    .param(WorkflowController.TYPE, ContentModerationWorkflowType.ID),
                    WorkflowController.managePath("approval"));

            assertThat(workflows.find("approval")).hasValueSatisfying(workflow ->
                    assertThat(workflow.transitions()).extracting(WorkflowTransition::id)
                            .containsExactly("create_new_draft", "publish"));
        }

        @Test
        void aWorkflowWithoutALabelOrOfAnUnknownTypeIsNotAdded() throws Exception {
            Document document = submit(post(WorkflowController.PATH + "/add")
                    .param(WorkflowController.LABEL, "")
                    .param(WorkflowController.TYPE, "retired_type"));

            assertThat(document.select(".is-invalid").eachAttr("name"))
                    .containsExactlyInAnyOrder(WorkflowController.LABEL, WorkflowController.TYPE);
            assertThat(workflows.all()).hasSize(1);
        }

        @Test
        void aWorkflowIsRenamed() throws Exception {
            redirects(post(EDITORIAL).param(WorkflowController.LABEL, "Publishing"), EDITORIAL);

            assertThat(editorial().label()).isEqualTo("Publishing");
        }

        @Test
        void aWorkflowWithoutALabelIsNotRenamed() throws Exception {
            Document document = submit(post(EDITORIAL).param(WorkflowController.LABEL, ""));

            assertThat(document.selectFirst("input[name=" + WorkflowController.LABEL + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(editorial().label()).isEqualTo("Editorial");
        }

        @Test
        void aWorkflowIsDeletedOnceConfirmed() throws Exception {
            assertThat(page(EDITORIAL + "/delete").select("button[type=submit]").text())
                    .isEqualTo("Delete workflow");

            redirects(post(EDITORIAL + "/delete"), WorkflowController.PATH);

            assertThat(workflows.find("editorial")).isEmpty();
        }

        @Test
        void anUnknownWorkflowIsNotFound() throws Exception {
            notFound(get(WorkflowController.managePath("missing")));
        }

        @Test
        void aWorkflowOfATypeTheSiteNoLongerHasIsMarkedSo() throws Exception {
            configStore.save(WorkflowConfig.configName("orphan"), new WorkflowConfig("orphan", "Orphan",
                    "retired_type", List.of(new WorkflowState("draft", "Draft", 0)), List.of()));

            Document document = page(WorkflowController.managePath("orphan"));

            assertThat(document.text()).contains("retired_type (missing)");
            assertThat(document.select("[data-state=draft] a:contains(Delete)")).isNotEmpty();
            assertThat(page(WorkflowController.managePath("orphan") + "/state/draft/delete")
                    .select("button[type=submit]")).isNotEmpty();
        }
    }

    @Nested
    class States {

        @Test
        void theWorkflowPageListsItsStatesAndOffersToDeleteOnlyTheOnesItsTypeDoesNotRequire() throws Exception {
            workflows.save(editorial().withState(new WorkflowState("archived", "Archived", 2)));

            Document document = page(EDITORIAL);

            assertThat(document.select("[data-state] td:first-child").eachText())
                    .containsExactly("Draft", "Published", "Archived");
            assertThat(document.select("[data-state=draft] a:contains(Delete)")).isEmpty();
            assertThat(document.select("[data-state=archived] a:contains(Delete)")).isNotEmpty();
        }

        @Test
        void aStateIsAddedWithAMachineNameFromItsLabel() throws Exception {
            assertThat(page(EDITORIAL + "/state/add").select("input[name=" + WorkflowController.LABEL + "]"))
                    .isNotEmpty();

            redirects(post(EDITORIAL + "/state/add").param(WorkflowController.LABEL, "In review")
                    .param(WorkflowController.WEIGHT, "1"), EDITORIAL);

            assertThat(editorial().state("in_review")).map(WorkflowState::weight).contains(1);
        }

        @Test
        void aStateWithoutALabelOrWeightIsNotAdded() throws Exception {
            Document document = submit(post(EDITORIAL + "/state/add").param(WorkflowController.LABEL, "")
                    .param(WorkflowController.WEIGHT, "heavy"));

            assertThat(document.select(".is-invalid").eachAttr("name"))
                    .containsExactlyInAnyOrder(WorkflowController.LABEL, WorkflowController.WEIGHT);
            assertThat(editorial().states()).hasSize(2);
        }

        @Test
        void aStateIsRelabeled() throws Exception {
            assertThat(page(EDITORIAL + "/state/draft").selectFirst("input[name=" + WorkflowController.LABEL + "]")
                    .val()).isEqualTo("Draft");

            redirects(post(EDITORIAL + "/state/draft").param(WorkflowController.LABEL, "Work in progress"), EDITORIAL);

            assertThat(editorial().state("draft")).map(WorkflowState::label).contains("Work in progress");
        }

        @Test
        void aStateIsDeletedOnceConfirmedWithTheTransitionsEndingThere() throws Exception {
            workflows.save(editorial().withState(new WorkflowState("archived", "Archived", 2)).withTransition(
                    new WorkflowTransition("archive", "Archive", List.of("published"), "archived", 2)));
            assertThat(page(EDITORIAL + "/state/archived/delete").select("button[type=submit]").text())
                    .isEqualTo("Delete state");

            redirects(post(EDITORIAL + "/state/archived/delete"), EDITORIAL);

            assertThat(editorial().state("archived")).isEmpty();
            assertThat(editorial().transition("archive")).isEmpty();
        }

        @Test
        void aRequiredOrUnknownStateCannotBeDeleted() throws Exception {
            notFound(get(EDITORIAL + "/state/draft/delete"));
            notFound(post(EDITORIAL + "/state/draft/delete"));
            notFound(get(EDITORIAL + "/state/missing"));
        }
    }

    @Nested
    class Transitions {

        @BeforeEach
        void anArchivedState() {
            workflows.save(editorial().withState(new WorkflowState("archived", "Archived", 2)));
        }

        @Test
        void theWorkflowPageListsEachTransitionWithItsStatesAndPermission() throws Exception {
            Document document = page(EDITORIAL);

            assertThat(document.selectFirst("[data-transition=publish]").text()).contains(
                    "Publish", "Draft, Published", WorkflowManager.transitionPermission("editorial", "publish"));
        }

        @Test
        void aTransitionIsAddedFromTheStatesTicked() throws Exception {
            assertThat(page(EDITORIAL + "/transition/add").select("input[type=checkbox]").eachAttr("name"))
                    .containsExactly(WorkflowController.FROM_PREFIX + "draft",
                            WorkflowController.FROM_PREFIX + "published",
                            WorkflowController.FROM_PREFIX + "archived");

            redirects(post(EDITORIAL + "/transition/add")
                    .param(WorkflowController.LABEL, "Archive")
                    .param(WorkflowController.FROM_PREFIX + "published", "on")
                    .param(WorkflowController.TO, "archived"), EDITORIAL);

            assertThat(editorial().transition("archive")).hasValueSatisfying(archive -> {
                assertThat(archive.from()).containsExactly("published");
                assertThat(archive.to()).isEqualTo("archived");
            });
        }

        @Test
        void aTransitionMakingAMoveAnotherAlreadyMakesIsNotAdded() throws Exception {
            Document document = submit(post(EDITORIAL + "/transition/add")
                    .param(WorkflowController.LABEL, "Go live")
                    .param(WorkflowController.FROM_PREFIX + "draft", "on")
                    .param(WorkflowController.TO, "published"));

            assertThat(document.text()).contains("Another transition already moves from draft to published.");
            assertThat(editorial().transition("go_live")).isEmpty();
        }

        @Test
        void aTransitionWithoutALabelIsNotAdded() throws Exception {
            Document document = submit(post(EDITORIAL + "/transition/add")
                    .param(WorkflowController.LABEL, "")
                    .param(WorkflowController.FROM_PREFIX + "published", "on")
                    .param(WorkflowController.TO, "archived"));

            assertThat(document.selectFirst("input[name=" + WorkflowController.LABEL + "]").hasClass("is-invalid"))
                    .isTrue();
            assertThat(editorial().transitions()).hasSize(2);
        }

        @Test
        void aTransitionIsChanged() throws Exception {
            assertThat(page(EDITORIAL + "/transition/publish")
                    .selectFirst("input[name=" + WorkflowController.FROM_PREFIX + "draft]").hasAttr("checked"))
                    .isTrue();

            redirects(post(EDITORIAL + "/transition/publish")
                    .param(WorkflowController.LABEL, "Go live")
                    .param(WorkflowController.FROM_PREFIX + "draft", "on")
                    .param(WorkflowController.TO, "published"), EDITORIAL);

            assertThat(editorial().transition("publish")).hasValueSatisfying(publish -> {
                assertThat(publish.label()).isEqualTo("Go live");
                assertThat(publish.from()).containsExactly("draft");
            });
        }

        @Test
        void aTransitionIsDeletedOnceConfirmed() throws Exception {
            assertThat(page(EDITORIAL + "/transition/publish/delete").select("button[type=submit]").text())
                    .isEqualTo("Delete transition");

            redirects(post(EDITORIAL + "/transition/publish/delete"), EDITORIAL);

            assertThat(editorial().transition("publish")).isEmpty();
        }

        @Test
        void anUnknownTransitionIsNotFound() throws Exception {
            notFound(get(EDITORIAL + "/transition/missing"));
        }
    }
}
