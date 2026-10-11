package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.action.ActionConfig;
import dev.springdrop.kernel.action.ActionManager;
import dev.springdrop.kernel.action.plugins.AssignNodeOwner;
import dev.springdrop.kernel.action.plugins.PublishNode;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ActionAdminIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActionManager actions;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private ConfigStore config;

    @BeforeEach
    void anAccountToAssign() {
        accounts.install();
        clear();
        accounts.create("grace", "grace@example.com", "");
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        config.listNames(ActionConfig.CONFIG_PREFIX).forEach(config::delete);
        accounts.findByName("grace").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(administrator())).andReturn().getResponse()
                .getContentAsString());
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private void storeAssignToGrace() {
        actions.save(new ActionConfig("make_grace_the_author", "Make Grace the author", AssignNodeOwner.ID,
                Map.of(AssignNodeOwner.OWNER, "grace")));
    }

    @Test
    void theListingShowsEveryActionWithEditAndDeleteOnlyForStoredOnes() throws Exception {
        storeAssignToGrace();

        Document listing = page(ActionAdminController.PATH);

        assertThat(listing.select("tr[data-action] td:first-child").eachText()).contains("Publish content",
                "Make Grace the author");
        assertThat(listing.select("tr[data-action=" + PublishNode.ID + "] a")).isEmpty();
        assertThat(listing.select("tr[data-action=make_grace_the_author] a").eachText())
                .containsExactly("Edit", "Delete");
        assertThat(listing.select("select[name=plugin] option").eachAttr("value"))
                .containsExactly(AssignNodeOwner.ID);
        BootstrapAssertions.assertNoOutlineButtons(listing);
        BootstrapAssertions.assertEditControlsAreButtons(listing);
    }

    @Test
    void creatingAConfigurableActionStoresItUnderAMachineNameFromItsLabel() throws Exception {
        Document form = page(ActionAdminController.ADD_PATH + "?plugin=" + AssignNodeOwner.ID);
        assertThat(form.selectFirst("input[name=owner]").hasAttr("required")).isTrue();

        MockHttpServletResponse saved = send(post(ActionAdminController.ADD_PATH)
                .param(ActionAdminController.PLUGIN, AssignNodeOwner.ID)
                .param(ActionAdminController.LABEL, "Make Grace the author")
                .param(AssignNodeOwner.OWNER, "grace"));

        assertThat(saved.getRedirectedUrl()).isEqualTo(ActionAdminController.PATH);
        assertThat(actions.find("make_grace_the_author")).hasValueSatisfying(action ->
                assertThat(action.configuration()).containsEntry(AssignNodeOwner.OWNER, "grace"));
    }

    @Test
    void anIdTakenByAStoredActionOrAPluginGetsASuffix() throws Exception {
        storeAssignToGrace();
        for (String label : List.of("Make Grace the author", "Node publish")) {
            send(post(ActionAdminController.ADD_PATH).param(ActionAdminController.PLUGIN, AssignNodeOwner.ID)
                    .param(ActionAdminController.LABEL, label).param(AssignNodeOwner.OWNER, "grace"));
        }

        assertThat(actions.find("make_grace_the_author_2")).isPresent();
        assertThat(actions.find("node_publish_2")).isPresent();
        assertThat(actions.find(PublishNode.ID).orElseThrow().label()).isEqualTo("Publish content");
    }

    @Test
    void anActionWithoutALabelOrWithAnUnknownAccountIsRefused() throws Exception {
        assertThat(send(post(ActionAdminController.ADD_PATH).param(ActionAdminController.PLUGIN, AssignNodeOwner.ID)
                .param(AssignNodeOwner.OWNER, "grace")).getContentAsString()).contains("This value is required.");
        assertThat(send(post(ActionAdminController.ADD_PATH).param(ActionAdminController.PLUGIN, AssignNodeOwner.ID)
                .param(ActionAdminController.LABEL, "Make Nobody the author").param(AssignNodeOwner.OWNER, "nobody"))
                .getContentAsString()).contains("There is no account named nobody.");

        assertThat(config.listNames(ActionConfig.CONFIG_PREFIX)).isEmpty();
    }

    @Test
    void onlyAConfigurablePluginCanBeCreated() throws Exception {
        assertThat(mockMvc.perform(get(ActionAdminController.ADD_PATH + "?plugin=" + PublishNode.ID)
                .with(administrator())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(send(post(ActionAdminController.ADD_PATH).param(ActionAdminController.PLUGIN, "node_archive")
                .param(ActionAdminController.LABEL, "Archive")).getStatus()).isEqualTo(404);
    }

    @Test
    void aStoredActionIsEditedWithItsSettingsFilledIn() throws Exception {
        storeAssignToGrace();

        Document form = page(ActionAdminController.managePath("make_grace_the_author"));
        assertThat(form.selectFirst("input[name=label]").val()).isEqualTo("Make Grace the author");
        assertThat(form.selectFirst("input[name=owner]").val()).isEqualTo("grace");

        send(post(ActionAdminController.managePath("make_grace_the_author"))
                .param(ActionAdminController.LABEL, "Credit Grace").param(AssignNodeOwner.OWNER, "grace"));

        assertThat(actions.find("make_grace_the_author").orElseThrow().label()).isEqualTo("Credit Grace");
    }

    @Test
    void aStoredActionIsDeletedAfterConfirmation() throws Exception {
        storeAssignToGrace();

        Document confirm = page(ActionAdminController.managePath("make_grace_the_author") + "/delete");
        assertThat(confirm.title()).isEqualTo("Delete the action Make Grace the author?");
        send(post(ActionAdminController.managePath("make_grace_the_author") + "/delete"));

        assertThat(actions.find("make_grace_the_author")).isEmpty();
    }

    @Test
    void aSimpleActionHasNoEditForm() throws Exception {
        assertThat(mockMvc.perform(get(ActionAdminController.managePath(PublishNode.ID)).with(administrator()))
                .andReturn().getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void theActionsAreClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(ActionAdminController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
