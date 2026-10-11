package dev.springdrop.web;

import dev.springdrop.kernel.status.RequirementSeverity;
import dev.springdrop.kernel.status.StatusReport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** The status report: every status check's lines, errors and warnings first, with a summary above. */
@Controller
public class StatusReportController {

    public static final String PATH = "/admin/reports/status";

    private final StatusReport report;

    public StatusReportController(StatusReport report) {
        this.report = report;
    }

    @GetMapping(PATH)
    public String report(Model model) {
        StatusReport.Summary summary = report.run();
        model.addAttribute("title", "Status report");
        model.addAttribute("summary", summary);
        return "admin/status-report";
    }

    /** The Bootstrap table row color a line's standing is drawn in. */
    public static String rowClass(RequirementSeverity severity) {
        return switch (severity) {
            case ERROR -> "table-danger";
            case WARNING -> "table-warning";
            case OK -> "table-success";
            case INFO -> "";
        };
    }
}
