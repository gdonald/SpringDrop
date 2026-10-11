package dev.springdrop.kernel.status;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Gathers every status check's lines, errors first, then warnings, then the
 * rest, each group by title, and sums them up.
 */
@Component
public class StatusReport {

    private final List<StatusCheck> checks;

    public StatusReport(List<StatusCheck> checks) {
        this.checks = checks;
    }

    /** The lines, and how many are errors and warnings. */
    public record Summary(List<Requirement> requirements, long errors, long warnings) {

        public Summary {
            requirements = List.copyOf(requirements);
        }
    }

    public Summary run() {
        List<Requirement> requirements = checks.stream()
                .flatMap(check -> check.requirements().stream())
                .sorted(Comparator.comparing(Requirement::severity).reversed().thenComparing(Requirement::title))
                .toList();
        return new Summary(requirements, count(requirements, RequirementSeverity.ERROR),
                count(requirements, RequirementSeverity.WARNING));
    }

    private static long count(List<Requirement> requirements, RequirementSeverity severity) {
        return requirements.stream().filter(requirement -> requirement.severity() == severity).count();
    }
}
