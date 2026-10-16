package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.log.ActivityLog;
import dev.springdrop.kernel.log.ErrorDisplay;
import dev.springdrop.kernel.log.LoggingSettings;
import dev.springdrop.kernel.theme.Messenger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The logging settings: how much an error page tells its reader, how many
 * events the database log keeps, and the backends events are written to.
 */
@Controller
public class LoggingSettingsController {

    public static final String PATH = "/admin/config/development/logging";

    public static final String ERROR_DISPLAY = "error_level";

    public static final String ROWS = "row_limit";

    public static final String BACKEND_PREFIX = "backend_";

    public static final List<SelectOption> DISPLAYS = List.of(
            new SelectOption(ErrorDisplay.NONE.name(), "None"),
            new SelectOption(ErrorDisplay.ERRORS.name(), "The error's message"),
            new SelectOption(ErrorDisplay.ALL.name(), "The error's message and stack trace"));

    public static final List<SelectOption> ROW_LIMITS = List.of(
            new SelectOption("0", "All"),
            new SelectOption("100", "100"),
            new SelectOption("1000", "1000"),
            new SelectOption("10000", "10000"),
            new SelectOption("100000", "100000"),
            new SelectOption("1000000", "1000000"));

    private final ActivityLog log;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public LoggingSettingsController(ActivityLog log, FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.log = log;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        LoggingSettings settings = log.settings();
        return page(loggingForm(settings.errorDisplay().name(), String.valueOf(settings.databaseLogRows()),
                settings.backends()), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String display = submitted.getOrDefault(ERROR_DISPLAY, "");
        String rows = submitted.getOrDefault(ROWS, "");
        List<String> backends = log.backendIds().stream()
                .filter(backend -> FormRenderer.CHECKED_VALUE.equals(submitted.get(BACKEND_PREFIX + backend))).toList();
        FormElement tree = loggingForm(display, rows, backends);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (Arrays.stream(ErrorDisplay.values()).noneMatch(each -> each.name().equals(display))) {
            state.error(ERROR_DISPLAY, "Choose how much error pages show.");
        }
        if (ROW_LIMITS.stream().noneMatch(option -> option.value().equals(rows))) {
            state.error(ROWS, "Choose how many events to keep.");
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        log.save(new LoggingSettings(backends, ErrorDisplay.valueOf(display), Integer.parseInt(rows)));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    private FormElement loggingForm(String display, String rows, List<String> backends) {
        FormElement written = FormElement.of(ElementType.FIELDSET, "backends").label("Write events to");
        log.backendIds().forEach(backend -> written.child(FormElement.of(ElementType.CHECKBOX,
                BACKEND_PREFIX + backend).label(backend).value(backends.contains(backend))));
        return FormElement.of(ElementType.CONTAINER, "logging")
                .child(FormElement.of(ElementType.SELECT, ERROR_DISPLAY).label("Error messages to display")
                        .description("What an error page shows about the error behind it, to everyone who meets it.")
                        .value(display).markRequired().options(DISPLAYS))
                .child(FormElement.of(ElementType.SELECT, ROWS).label("Database log messages to keep")
                        .description("Cron removes the oldest beyond this many.")
                        .value(rows).markRequired().options(ROW_LIMITS))
                .child(written)
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Logging and errors");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/settings-form";
    }
}
