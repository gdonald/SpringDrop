package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.datetime.SiteDateSettings;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.theme.Messenger;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The regional settings: the site's country, the day its week starts on, and its default timezone. */
@Controller
public class RegionalSettingsController {

    public static final String PATH = "/admin/config/regional/settings";

    public static final String COUNTRY = "country";

    public static final String FIRST_DAY = "first_day";

    public static final String TIMEZONE = "timezone";

    private final ConfigStore config;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public RegionalSettingsController(ConfigStore config, FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.config = config;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        SiteDateSettings settings = settings();
        return page(regionalForm(settings.country(), settings.firstDayOfWeek().name(), settings.defaultTimezone()),
                Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String country = submitted.getOrDefault(COUNTRY, "");
        String firstDay = submitted.getOrDefault(FIRST_DAY, "");
        String timezone = submitted.getOrDefault(TIMEZONE, "");
        FormElement tree = regionalForm(country, firstDay, timezone);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!country.isEmpty() && !Arrays.asList(Locale.getISOCountries()).contains(country)) {
            state.error(COUNTRY, "Choose a country.");
        }
        if (Arrays.stream(DayOfWeek.values()).noneMatch(day -> day.name().equals(firstDay))) {
            state.error(FIRST_DAY, "Choose a day.");
        }
        if (!ZoneId.getAvailableZoneIds().contains(timezone)) {
            state.error(TIMEZONE, "Choose a timezone.");
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        config.save(SiteDateSettings.CONFIG_NAME, new SiteDateSettings(timezone, country, DayOfWeek.valueOf(firstDay)));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    private SiteDateSettings settings() {
        return config.read(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class, SiteDateSettings.DEFAULTS);
    }

    private static FormElement regionalForm(String country, String firstDay, String timezone) {
        List<SelectOption> countries = new ArrayList<>(List.of(new SelectOption("", "None")));
        Arrays.stream(Locale.getISOCountries())
                .map(code -> new SelectOption(code, Locale.of("", code).getDisplayCountry(Locale.ENGLISH)))
                .sorted((left, right) -> left.label().compareTo(right.label()))
                .forEach(countries::add);
        List<SelectOption> days = new ArrayList<>();
        DayOfWeek day = DayOfWeek.SUNDAY;
        for (int place = 0; place < DayOfWeek.values().length; place++) {
            days.add(new SelectOption(day.name(), day.getDisplayName(TextStyle.FULL, Locale.ENGLISH)));
            day = day.plus(1);
        }
        return FormElement.of(ElementType.CONTAINER, "regional-settings")
                .child(FormElement.of(ElementType.FIELDSET, "locale").label("Locale")
                        .child(FormElement.of(ElementType.SELECT, COUNTRY).label("Default country")
                                .value(country).options(countries))
                        .child(FormElement.of(ElementType.SELECT, FIRST_DAY).label("First day of week")
                                .description("The day calendars in date fields start their weeks on.")
                                .value(firstDay).markRequired().options(days)))
                .child(FormElement.of(ElementType.FIELDSET, "time-zones").label("Time zones")
                        .child(FormElement.of(ElementType.SELECT, TIMEZONE).label("Default timezone")
                                .value(timezone).markRequired()
                                .options(ZoneId.getAvailableZoneIds().stream().sorted()
                                        .map(zone -> new SelectOption(zone, zone)).toList())))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Regional settings");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/settings-form";
    }
}
