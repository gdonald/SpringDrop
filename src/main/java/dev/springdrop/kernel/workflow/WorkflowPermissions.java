package dev.springdrop.kernel.workflow;

import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** One permission per transition of every workflow, built from the workflows the site has. */
@Component
public class WorkflowPermissions implements PermissionProvider {

    public static final String PROVIDER = "workflows";

    private final WorkflowManager workflows;

    public WorkflowPermissions(WorkflowManager workflows) {
        this.workflows = workflows;
    }

    @Override
    public List<PermissionDefinition> permissions() {
        List<PermissionDefinition> permissions = new ArrayList<>();
        for (WorkflowConfig workflow : workflows.all()) {
            for (WorkflowTransition transition : workflow.transitions()) {
                permissions.add(PermissionDefinition.of(
                        WorkflowManager.transitionPermission(workflow.id(), transition.id()),
                        workflow.label() + " workflow: Use " + transition.label() + " transition.",
                        PROVIDER));
            }
        }
        return List.copyOf(permissions);
    }
}
