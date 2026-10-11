package dev.springdrop.kernel.status;

/** How a status report line stands, from a plain fact to something that needs fixing. */
public enum RequirementSeverity {
    INFO,
    OK,
    WARNING,
    ERROR
}
