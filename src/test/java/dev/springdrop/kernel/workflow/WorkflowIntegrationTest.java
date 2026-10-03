package dev.springdrop.kernel.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.permission.PermissionRegistry;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.workflow.types.ContentModerationWorkflowType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

@SpringBootTest
class WorkflowIntegrationTest extends AbstractIntegrationTest {

    private static final String EDITOR = "editor";

    private static final String WRITER = "writer";

    @Autowired
    private WorkflowManager workflows;

    @Autowired
    private RoleManager roles;

    @Autowired
    private PermissionRegistry permissions;

    private WorkflowConfig editorial;

    @BeforeEach
    void anEditorialWorkflowWhosePublishingOnlyEditorsMayDo() {
        roles.install();
        editorial = workflows.create("editorial", "Editorial", ContentModerationWorkflowType.ID);
        assertThat(workflows.save(editorial)).isEmpty();
        roles.save(RoleConfig.of(EDITOR, "Editor", 2));
        roles.save(RoleConfig.of(WRITER, "Writer", 3));
        roles.grant(EDITOR, WorkflowManager.transitionPermission("editorial", "publish"));
        roles.grant(EDITOR, WorkflowManager.transitionPermission("editorial", "create_new_draft"));
        roles.grant(WRITER, WorkflowManager.transitionPermission("editorial", "create_new_draft"));
    }

    @AfterEach
    void noWorkflowsLeft() {
        workflows.all().forEach(workflow -> workflows.delete(workflow.id()));
        roles.delete(EDITOR);
        roles.delete(WRITER);
    }

    private Authentication holding(long id, String role) {
        AccountPrincipal principal = new AccountPrincipal(id, role, "", true, roles.permissionsOf(List.of(role)),
                List.of(role));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    @Test
    void aTransitionIsAllowedToARoleGrantedItAndRefusedToOneThatIsNot() {
        assertThat(workflows.refusal(editorial, "draft", "published", holding(7L, EDITOR))).isEmpty();
        assertThat(workflows.refusal(editorial, "draft", "published", holding(8L, WRITER)))
                .contains("You may not use the Publish transition.");
    }

    @Test
    void aMoveNoTransitionMakesIsRefusedToEveryone() {
        WorkflowConfig noPublishing = editorial.withoutTransition("publish");

        assertThat(workflows.refusal(noPublishing, "draft", "published", holding(7L, EDITOR)))
                .contains("There is no transition from draft to published.");
    }

    @Test
    void theFirstAccountMayUseEveryTransition() {
        Authentication first = holding(UserAccount.ADMINISTRATOR_ID, WRITER);

        assertThat(workflows.usable(editorial, "draft", first)).extracting(WorkflowTransition::id)
                .containsExactly("create_new_draft", "publish");
    }

    @Test
    void theTransitionsSomeoneMayUseAreTheOnesTheyHoldThePermissionFor() {
        assertThat(workflows.usable(editorial, "draft", holding(8L, WRITER))).extracting(WorkflowTransition::id)
                .containsExactly("create_new_draft");
    }

    @Test
    void eachTransitionOffersItsOwnPermission() {
        assertThat(permissions.providedBy(WorkflowPermissions.PROVIDER))
                .extracting(permission -> permission.title())
                .containsExactly("Editorial workflow: Use Create new draft transition.",
                        "Editorial workflow: Use Publish transition.");
    }

    @Test
    void aWorkflowThatDropsAStateItsTypeRequiresIsNotSaved() {
        WorkflowConfig withoutDrafts = editorial.withoutState("draft").withLabel("Changed");

        assertThat(workflows.save(withoutDrafts))
                .containsExactly("A Content moderation workflow has to keep the draft state.");
        assertThat(workflows.find("editorial")).map(WorkflowConfig::label).contains("Editorial");
    }

    @Test
    void aWorkflowOfATypeTheSiteDoesNotHaveIsNotSaved() {
        WorkflowConfig unknown = new WorkflowConfig("unknown", "Unknown", "retired_type", List.of(), List.of());

        assertThat(workflows.save(unknown)).containsExactly("The workflow type retired_type is not one the site has.");
        assertThat(workflows.find("unknown")).isEmpty();
    }

    @Test
    void workflowsAndTheirTypesAreListedByLabel() {
        workflows.save(workflows.create("approval", "Approval", ContentModerationWorkflowType.ID));

        assertThat(workflows.all()).extracting(WorkflowConfig::label).containsExactly("Approval", "Editorial");
        assertThat(workflows.types()).extracting(WorkflowTypeDefinition::id)
                .contains(ContentModerationWorkflowType.ID);
    }
}
