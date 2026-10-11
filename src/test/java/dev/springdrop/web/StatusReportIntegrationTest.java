package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.state.StateService;
import dev.springdrop.kernel.status.RequirementSeverity;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.time.Duration;
import java.time.Instant;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class StatusReportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StateService state;

    @AfterEach
    void forgetTheLastRun() {
        state.remove("system", "cron_last");
    }

    private Document report() throws Exception {
        return Jsoup.parse(mockMvc.perform(get(StatusReportController.PATH).with(user("reporter")
                .authorities(new SimpleGrantedAuthority(Permissions.ACCESS_SITE_REPORTS)))).andReturn().getResponse()
                .getContentAsString());
    }

    private static Element row(Document report, String title) {
        return report.select("table.status-requirements tbody tr").stream()
                .filter(row -> row.selectFirst("th").text().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void anOverdueCronRunIsFlaggedWithALinkToRunIt() throws Exception {
        state.set("system", "cron_last", Instant.now().minus(Duration.ofDays(3)).getEpochSecond());

        Document report = report();

        Element cron = row(report, "Cron maintenance tasks");
        assertThat(cron.attr("data-severity")).isEqualTo(RequirementSeverity.WARNING.name());
        assertThat(cron.hasClass("table-warning")).isTrue();
        assertThat(cron.selectFirst("a.btn").attr("href")).isEqualTo(CronController.PATH);
        assertThat(Integer.parseInt(report.selectFirst(".status-warnings").text())).isPositive();
        assertThat(report.select("table.status-requirements tbody tr").first().attr("data-severity"))
                .isIn(RequirementSeverity.ERROR.name(), RequirementSeverity.WARNING.name());
        BootstrapAssertions.assertNoOutlineButtons(report);
    }

    @Test
    void eachStandingHasARowColor() {
        assertThat(StatusReportController.rowClass(RequirementSeverity.ERROR)).isEqualTo("table-danger");
        assertThat(StatusReportController.rowClass(RequirementSeverity.WARNING)).isEqualTo("table-warning");
        assertThat(StatusReportController.rowClass(RequirementSeverity.OK)).isEqualTo("table-success");
        assertThat(StatusReportController.rowClass(RequirementSeverity.INFO)).isEmpty();
    }

    @Test
    void theReportIsClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(StatusReportController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
