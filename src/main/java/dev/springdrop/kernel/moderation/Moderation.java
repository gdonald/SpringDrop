package dev.springdrop.kernel.moderation;

import dev.springdrop.kernel.workflow.WorkflowConfig;

/** The workflow moderating a content type, with what its states mean. */
public record Moderation(WorkflowConfig workflow, ModerationConfig config) {
}
