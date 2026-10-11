package dev.springdrop.web;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.log.DatabaseLog;
import dev.springdrop.kernel.log.LogEntry;
import dev.springdrop.kernel.log.LogFilter;
import dev.springdrop.kernel.log.Severity;
import dev.springdrop.kernel.theme.Pager;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The activity log as the database keeps it: the newest events first, fifty
 * a page, filtered by type and severity, each with a page of its own, and a
 * button that clears the log after confirmation.
 */
@Controller
public class ActivityLogController {

    public static final String PATH = "/admin/reports/dblog";

    public static final String CLEAR_PATH = PATH + "/clear";

    public static final String TYPE = "type";

    public static final String SEVERITY = "severity";

    public static final String PAGE = "page";

    public static final int PAGE_SIZE = 50;

    /** How much of a message the listing shows before cutting it short. */
    public static final int MESSAGE_LENGTH = 56;

    private final DatabaseLog log;
    private final UserAccountService accounts;
    private final DateFormatService dates;

    public ActivityLogController(DatabaseLog log, UserAccountService accounts, DateFormatService dates) {
        this.log = log;
        this.accounts = accounts;
        this.dates = dates;
    }

    public static String eventPath(long id) {
        return PATH + "/event/" + id;
    }

    /** A row of the listing. */
    public record EventRow(long id, String severity, String severityClass, String type, String date,
            String message, String user) {
    }

    /** An option of a filter select, and whether it is chosen. */
    public record Choice(String value, String label, boolean selected) {
    }

    @GetMapping(PATH)
    public String list(@RequestParam(name = TYPE, defaultValue = "") String type,
            @RequestParam(name = SEVERITY, defaultValue = "") String severity,
            @RequestParam(name = PAGE, defaultValue = "1") String page, Model model) {
        Optional<Severity> chosenSeverity = severity.matches("[0-7]")
                ? Severity.ofLevel(Integer.parseInt(severity)) : Optional.empty();
        LogFilter filter = new LogFilter(type.isEmpty() ? Optional.empty() : Optional.of(type), chosenSeverity);
        int total = log.count(filter);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = page.matches("\\d{1,6}") ? Math.min(Math.max(Integer.parseInt(page), 1), pages) : 1;

        model.addAttribute("title", "Recent log messages");
        model.addAttribute("rows", log.page(filter, (current - 1) * PAGE_SIZE, PAGE_SIZE).stream()
                .map(this::row).toList());
        model.addAttribute("types", log.types().stream().map(each -> new Choice(each, each, each.equals(type)))
                .toList());
        model.addAttribute("severities", Arrays.stream(Severity.values())
                .map(each -> new Choice(String.valueOf(each.level()), each.label(),
                        chosenSeverity.filter(each::equals).isPresent()))
                .toList());
        model.addAttribute("pager", pages > 1
                ? Pager.of(current, pages, number -> pageUrl(type, chosenSeverity, number)) : Pager.NONE);
        model.addAttribute("action", PATH);
        model.addAttribute("clearPath", CLEAR_PATH);
        return "admin/activity-log";
    }

    @GetMapping(PATH + "/event/{id}")
    public String event(@PathVariable long id, Model model) {
        LogEntry entry = log.find(id).orElseThrow(() -> new EntityNotFoundException("log event", String.valueOf(id)));
        model.addAttribute("title", "Details");
        model.addAttribute("details", List.of(
                List.of("Type", entry.type()),
                List.of("Date", dates.format(entry.timestamp(), "long", null)),
                List.of("User", userName(entry.uid())),
                List.of("Location", entry.location()),
                List.of("Referrer", entry.referer()),
                List.of("Message", entry.formatted()),
                List.of("Severity", entry.severity().label()),
                List.of("Hostname", entry.hostname())));
        model.addAttribute("backPath", PATH);
        return "admin/activity-log-event";
    }

    @GetMapping(CLEAR_PATH)
    public String confirmClear(Model model) {
        model.addAttribute("title", "Clear the log?");
        model.addAttribute("description", "Every message in the log is removed. This action cannot be undone.");
        model.addAttribute("action", CLEAR_PATH);
        model.addAttribute("confirmLabel", "Clear log messages");
        model.addAttribute("cancelPath", PATH);
        return "admin/confirm";
    }

    @PostMapping(CLEAR_PATH)
    public String clear() {
        log.clear();
        return "redirect:" + PATH;
    }

    /** The address of one page of the listing, keeping the filters chosen. */
    private static String pageUrl(String type, Optional<Severity> severity, int number) {
        UriComponentsBuilder url = UriComponentsBuilder.fromPath(PATH);
        if (!type.isEmpty()) {
            url.queryParam(TYPE, type);
        }
        severity.ifPresent(chosen -> url.queryParam(SEVERITY, chosen.level()));
        return url.queryParam(PAGE, number).build().encode().toUriString();
    }

    private EventRow row(LogEntry entry) {
        String message = entry.formatted();
        return new EventRow(entry.id(), entry.severity().label(), severityClass(entry.severity()), entry.type(),
                dates.format(entry.timestamp(), "short", null),
                message.length() > MESSAGE_LENGTH ? message.substring(0, MESSAGE_LENGTH) + "..." : message,
                userName(entry.uid()));
    }

    /** The Bootstrap color a severity is badged in. */
    static String severityClass(Severity severity) {
        return switch (severity) {
            case EMERGENCY, ALERT, CRITICAL, ERROR -> "text-bg-danger";
            case WARNING -> "text-bg-warning";
            case NOTICE -> "text-bg-info";
            case INFO, DEBUG -> "text-bg-secondary";
        };
    }

    private String userName(long uid) {
        return accounts.find(uid).map(UserAccount::name).orElse(UserAccount.ANONYMOUS_NAME);
    }
}
