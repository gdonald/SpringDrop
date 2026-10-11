package dev.springdrop.kernel.status;

/**
 * One line of the status report: what is checked, its current value, what it
 * means, how it stands, and a link to where it is fixed. The link's label and
 * path are empty when there is nowhere to go.
 */
public record Requirement(String title, String value, String description, RequirementSeverity severity,
        String linkLabel, String linkPath) {

    public static Requirement of(String title, String value, String description, RequirementSeverity severity) {
        return new Requirement(title, value, description, severity, "", "");
    }

    public Requirement linkedTo(String label, String path) {
        return new Requirement(title, value, description, severity, label, path);
    }
}
