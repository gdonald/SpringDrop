package dev.springdrop.kernel.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StatusReportTest {

    @Test
    void errorsComeFirstThenWarningsThenTheRestEachByTitleAndAreCounted() {
        StatusCheck first = () -> List.of(
                Requirement.of("Mail server", "Not configured", "", RequirementSeverity.WARNING),
                Requirement.of("Database", "PostgreSQL 17", "", RequirementSeverity.OK));
        StatusCheck second = () -> List.of(
                Requirement.of("Cron maintenance tasks", "Never run", "", RequirementSeverity.ERROR),
                Requirement.of("Cache backend", "ConcurrentMapCacheManager", "", RequirementSeverity.INFO),
                Requirement.of("Account registration", "Open", "", RequirementSeverity.WARNING));

        StatusReport.Summary summary = new StatusReport(List.of(first, second)).run();

        assertThat(summary.requirements()).extracting(Requirement::title).containsExactly("Cron maintenance tasks",
                "Account registration", "Mail server", "Database", "Cache backend");
        assertThat(summary.errors()).isEqualTo(1);
        assertThat(summary.warnings()).isEqualTo(2);
    }

    @Test
    void aLineLinksToWhereItIsFixed() {
        Requirement linked = Requirement.of("Cron", "Never run", "", RequirementSeverity.ERROR)
                .linkedTo("Run cron", "/admin/config/system/cron");

        assertThat(linked.linkLabel()).isEqualTo("Run cron");
        assertThat(linked.linkPath()).isEqualTo("/admin/config/system/cron");
    }
}
