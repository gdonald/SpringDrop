package dev.springdrop.kernel.workflow;

import java.util.List;

/**
 * One move a workflow allows: its machine name and label, the states it starts
 * from, the state it ends in, and where it sorts.
 */
public record WorkflowTransition(String id, String label, List<String> from, String to, int weight) {

    public WorkflowTransition {
        from = List.copyOf(from);
    }

    public boolean leads(String fromState, String toState) {
        return from.contains(fromState) && to.equals(toState);
    }
}
