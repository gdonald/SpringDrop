package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.site.RssSettings;
import dev.springdrop.kernel.theme.Messenger;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** How the site's feeds are published: the description each carries and how many items each holds. */
@Controller
public class RssSettingsController {

    public static final String PATH = "/admin/config/services/rss-publishing";

    public static final String DESCRIPTION = "feed_description";

    public static final String ITEMS = "feed_items";

    private final ConfigStore config;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public RssSettingsController(ConfigStore config, FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.config = config;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        RssSettings settings = config.read(RssSettings.CONFIG_NAME, RssSettings.class, RssSettings.DEFAULTS);
        return page(rssForm(settings.description(), String.valueOf(settings.items())), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String description = submitted.getOrDefault(DESCRIPTION, "").strip();
        String items = submitted.getOrDefault(ITEMS, "").strip();
        FormElement tree = rssForm(description, items);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        config.save(RssSettings.CONFIG_NAME, new RssSettings(Integer.parseInt(items), description));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    private static FormElement rssForm(String description, String items) {
        return FormElement.of(ElementType.CONTAINER, "rss-settings")
                .child(FormElement.of(ElementType.TEXTAREA, DESCRIPTION).label("Feed description")
                        .description("Carried by each feed. Blank uses the site name.")
                        .value(description).rule(ValidationRule.maxLength(1000)))
                .child(FormElement.of(ElementType.NUMBER, ITEMS).label("Number of items in each feed")
                        .value(items).markRequired()
                        .rule(ValidationRule.pattern("\\d{1,3}").withMessage("Enter a whole number."))
                        .rule(ValidationRule.range("1", String.valueOf(RssSettings.MAX_ITEMS))))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "RSS publishing");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/settings-form";
    }
}
