package dev.springdrop.web;

/** One transition on a workflow's page: its id and label, the states it joins, and the permission guarding it. */
public record WorkflowTransitionRow(String id, String label, String from, String to, String permission) {
}
