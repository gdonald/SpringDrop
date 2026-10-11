package dev.springdrop.web;

import dev.springdrop.kernel.cron.AutomatedCron;
import dev.springdrop.kernel.cron.AutomatedCronSettings;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The performance settings: how often page requests start automated cron. */
@Controller
public class PerformanceController {

    public static final String PATH = "/admin/config/development/performance";

    public static final String AUTOMATED_CRON_INTERVAL = "automated_cron_interval";

    public static final List<SelectOption> INTERVALS = List.of(
            new SelectOption("0", "Never"),
            new SelectOption("3600", "Every hour"),
            new SelectOption("10800", "Every 3 hours"),
            new SelectOption("21600", "Every 6 hours"),
            new SelectOption("43200", "Every 12 hours"),
            new SelectOption("86400", "Every day"),
            new SelectOption("604800", "Every week"));

    private final AutomatedCron automatedCron;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public PerformanceController(AutomatedCron automatedCron, FormBuilder formBuilder, FormRenderer renderer) {
        this.automatedCron = automatedCron;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        return page(settingsForm(String.valueOf(automatedCron.settings().intervalSeconds())), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String interval = submitted.getOrDefault(AUTOMATED_CRON_INTERVAL, "");
        FormElement tree = settingsForm(interval);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        boolean offered = INTERVALS.stream().anyMatch(option -> option.value().equals(interval));
        if (!interval.isEmpty() && !offered) {
            state.error(AUTOMATED_CRON_INTERVAL, "Choose an interval.");
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        automatedCron.save(new AutomatedCronSettings(Long.parseLong(interval)));
        return "redirect:" + PATH;
    }

    private static FormElement settingsForm(String interval) {
        return FormElement.of(ElementType.CONTAINER, "performance")
                .child(FormElement.of(ElementType.SELECT, AUTOMATED_CRON_INTERVAL).label("Run cron every")
                        .markRequired()
                        .value(interval)
                        .description("A page request starts cron once this long has passed since it last ran.")
                        .options(INTERVALS))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Performance");
        model.addAttribute("description", "Settings for how the site does its background work.");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }
}
