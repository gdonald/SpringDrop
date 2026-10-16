package dev.springdrop.web;

import dev.springdrop.kernel.asset.Assets;
import dev.springdrop.kernel.cache.Performance;
import dev.springdrop.kernel.cache.PerformanceSettings;
import dev.springdrop.kernel.cron.AutomatedCron;
import dev.springdrop.kernel.cron.AutomatedCronSettings;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.theme.Messenger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The performance settings: how long pages are kept in the page cache,
 * whether scripts load as one aggregated bundle, and how often page requests
 * start automated cron, and a button that clears every cache.
 */
@Controller
public class PerformanceController {

    public static final String PATH = "/admin/config/development/performance";

    public static final String CLEAR_PATH = PATH + "/clear";

    public static final String AUTOMATED_CRON_INTERVAL = "automated_cron_interval";

    public static final String PAGE_CACHE_MAX_AGE = "page_cache_maximum_age";

    public static final String AGGREGATE_JS = "preprocess_js";

    static final String NO_BUNDLE = "The build made no script bundle, so scripts cannot be aggregated.";

    public static final List<SelectOption> MAX_AGES = List.of(
            new SelectOption("0", "No caching"),
            new SelectOption("60", "1 minute"),
            new SelectOption("300", "5 minutes"),
            new SelectOption("600", "10 minutes"),
            new SelectOption("1800", "30 minutes"),
            new SelectOption("3600", "1 hour"),
            new SelectOption("21600", "6 hours"),
            new SelectOption("86400", "1 day"));

    public static final List<SelectOption> INTERVALS = List.of(
            new SelectOption("0", "Never"),
            new SelectOption("3600", "Every hour"),
            new SelectOption("10800", "Every 3 hours"),
            new SelectOption("21600", "Every 6 hours"),
            new SelectOption("43200", "Every 12 hours"),
            new SelectOption("86400", "Every day"),
            new SelectOption("604800", "Every week"));

    private final AutomatedCron automatedCron;
    private final Performance performance;
    private final Assets assets;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public PerformanceController(AutomatedCron automatedCron, Performance performance, Assets assets,
            FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.automatedCron = automatedCron;
        this.performance = performance;
        this.assets = assets;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        PerformanceSettings settings = performance.settings();
        return page(settingsForm(String.valueOf(automatedCron.settings().intervalSeconds()),
                String.valueOf(settings.pageCacheMaxAge()), settings.aggregateJavaScript()), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String interval = submitted.getOrDefault(AUTOMATED_CRON_INTERVAL, "");
        String maxAge = submitted.getOrDefault(PAGE_CACHE_MAX_AGE, "");
        boolean aggregate = FormRenderer.CHECKED_VALUE.equals(submitted.get(AGGREGATE_JS));
        FormElement tree = settingsForm(interval, maxAge, aggregate);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!interval.isEmpty() && INTERVALS.stream().noneMatch(option -> option.value().equals(interval))) {
            state.error(AUTOMATED_CRON_INTERVAL, "Choose an interval.");
        }
        if (!maxAge.isEmpty() && MAX_AGES.stream().noneMatch(option -> option.value().equals(maxAge))) {
            state.error(PAGE_CACHE_MAX_AGE, "Choose how long pages are kept.");
        }
        if (aggregate && !assets.bundleBuilt()) {
            state.error(AGGREGATE_JS, NO_BUNDLE);
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        automatedCron.save(new AutomatedCronSettings(Long.parseLong(interval)));
        performance.save(new PerformanceSettings(Long.parseLong(maxAge), aggregate));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    @PostMapping(CLEAR_PATH)
    public String clearCaches() {
        performance.clearCaches();
        messenger.success("Caches cleared.");
        return "redirect:" + PATH;
    }

    private FormElement settingsForm(String interval, String maxAge, boolean aggregate) {
        FormElement aggregation = FormElement.of(ElementType.CHECKBOX, AGGREGATE_JS).label("Aggregate JavaScript files")
                .description(assets.bundleBuilt() ? "Pages load the site's scripts as one minified bundle."
                        : NO_BUNDLE)
                .value(aggregate);
        if (!assets.bundleBuilt()) {
            aggregation.attribute("disabled", "disabled");
        }
        return FormElement.of(ElementType.CONTAINER, "performance")
                .child(FormElement.of(ElementType.FIELDSET, "caching").label("Caching")
                        .child(FormElement.of(ElementType.SELECT, PAGE_CACHE_MAX_AGE).label("Page cache maximum age")
                                .markRequired().value(maxAge)
                                .description("How long a page is kept before it is built again.")
                                .options(MAX_AGES)))
                .child(FormElement.of(ElementType.FIELDSET, "bandwidth").label("Bandwidth optimization")
                        .child(aggregation))
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
        model.addAttribute("action", PATH);
        model.addAttribute("clearAction", CLEAR_PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/performance";
    }
}
