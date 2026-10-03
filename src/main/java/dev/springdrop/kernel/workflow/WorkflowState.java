package dev.springdrop.kernel.workflow;

/** One state something in a workflow can be in: its machine name, its label, and where it sorts. */
public record WorkflowState(String id, String label, int weight) {
}
