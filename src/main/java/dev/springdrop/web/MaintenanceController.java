package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.maintenance.Maintenance;
import dev.springdrop.kernel.maintenance.MaintenanceSettings;
import dev.springdrop.kernel.theme.Messenger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Putting the site into maintenance mode and taking it out, with the message visitors see meanwhile. */
@Controller
public class MaintenanceController {

    public static final String PATH = "/admin/config/development/maintenance";

    public static final String MODE = "maintenance_mode";

    public static final String MESSAGE = "maintenance_mode_message";

    private final Maintenance maintenance;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public MaintenanceController(Maintenance maintenance, FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.maintenance = maintenance;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        MaintenanceSettings settings = maintenance.settings();
        return page(maintenanceForm(settings.enabled(), settings.message()), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        boolean enabled = FormRenderer.CHECKED_VALUE.equals(submitted.get(MODE));
        String message = submitted.getOrDefault(MESSAGE, "").strip();
        FormElement tree = maintenanceForm(enabled, message);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        maintenance.save(new MaintenanceSettings(enabled, message));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    private static FormElement maintenanceForm(boolean enabled, String message) {
        return FormElement.of(ElementType.CONTAINER, "maintenance")
                .child(FormElement.of(ElementType.CHECKBOX, MODE).label("Put site into maintenance mode")
                        .description("Visitors see the message below. Those who may use the site in maintenance "
                                + "mode, and the first account, see the site as usual.")
                        .value(enabled))
                .child(FormElement.of(ElementType.TEXTAREA, MESSAGE).label("Message to display when in maintenance mode")
                        .value(message)
                        .requiredWhen(MODE, FormRenderer.CHECKED_VALUE)
                        .rule(ValidationRule.maxLength(1000)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Maintenance mode");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/settings-form";
    }
}
