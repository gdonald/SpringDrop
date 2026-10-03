package dev.springdrop.kernel.workflow;

import java.util.List;

/**
 * A kind of workflow: what it is called, the states and transitions a new
 * workflow of it starts with, and the states every workflow of it has to keep.
 * A module contributes one by annotating it with
 * {@code @SpringDropPlugin(type = WorkflowType.class)}.
 */
public interface WorkflowType {

    String label();

    /** A new workflow of this type, holding the states and transitions it starts with. */
    WorkflowConfig initial(String id, String label);

    /** The ids of the states a workflow of this type cannot do without. */
    List<String> requiredStates();
}
