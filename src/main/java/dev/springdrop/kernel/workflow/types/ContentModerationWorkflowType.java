package dev.springdrop.kernel.workflow.types;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowState;
import dev.springdrop.kernel.workflow.WorkflowTransition;
import dev.springdrop.kernel.workflow.WorkflowType;
import java.util.List;

/**
 * Moderating content through states before it is published. A workflow of
 * this type always has a draft state and a published state, and starts with
 * the transitions between them.
 */
@SpringDropPlugin(id = ContentModerationWorkflowType.ID, type = WorkflowType.class)
public class ContentModerationWorkflowType implements WorkflowType {

    public static final String ID = "content_moderation";

    public static final String DRAFT = "draft";

    public static final String PUBLISHED = "published";

    @Override
    public String label() {
        return "Content moderation";
    }

    @Override
    public WorkflowConfig initial(String id, String label) {
        return new WorkflowConfig(id, label, ID,
                List.of(new WorkflowState(DRAFT, "Draft", 0), new WorkflowState(PUBLISHED, "Published", 1)),
                List.of(new WorkflowTransition("create_new_draft", "Create new draft",
                                List.of(DRAFT, PUBLISHED), DRAFT, 0),
                        new WorkflowTransition("publish", "Publish", List.of(DRAFT, PUBLISHED), PUBLISHED, 1)));
    }

    @Override
    public List<String> requiredStates() {
        return List.of(DRAFT, PUBLISHED);
    }
}
