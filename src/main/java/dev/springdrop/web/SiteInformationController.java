package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.datetime.SiteDateSettings;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.web.ErrorPagesConfig;
import dev.springdrop.kernel.theme.Messenger;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The site information form: the site's name, slogan, and email address, the
 * path the home page shows, the paths shown for a refused or unmatched
 * request, and the default timezone and language.
 */
@Controller
public class SiteInformationController {

    public static final String PATH = "/admin/config/system/site-information";

    public static final String NAME = "site_name";

    public static final String SLOGAN = "site_slogan";

    public static final String MAIL = "site_mail";

    public static final String FRONT_PAGE = "front_page";

    public static final String FORBIDDEN_PAGE = "page_403";

    public static final String NOT_FOUND_PAGE = "page_404";

    public static final String TIMEZONE = "timezone";

    public static final String LANGUAGE = "default_langcode";

    /** A site path: a slash and then anything but whitespace. */
    static final String PATH_PATTERN = "/\\S*";

    private final ConfigStore config;
    private final LanguageManager languages;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public SiteInformationController(ConfigStore config, LanguageManager languages, FormBuilder formBuilder,
            FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.config = config;
        this.languages = languages;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        SiteInformation site = site();
        ErrorPagesConfig errorPages = config.read(ErrorPagesConfig.CONFIG_NAME, ErrorPagesConfig.class,
                ErrorPagesConfig.DEFAULTS);
        Map<String, String> current = new LinkedHashMap<>();
        current.put(NAME, site.name());
        current.put(SLOGAN, site.slogan());
        current.put(MAIL, site.mail());
        current.put(FRONT_PAGE, site.frontPage());
        current.put(FORBIDDEN_PAGE, errorPages.forbiddenPath());
        current.put(NOT_FOUND_PAGE, errorPages.notFoundPath());
        current.put(TIMEZONE, config.read(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class,
                SiteDateSettings.DEFAULTS).defaultTimezone());
        current.put(LANGUAGE, languages.defaultLangcode());
        return page(siteForm(current), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : List.of(NAME, SLOGAN, MAIL, FRONT_PAGE, FORBIDDEN_PAGE, NOT_FOUND_PAGE, TIMEZONE,
                LANGUAGE)) {
            values.put(name, submitted.getOrDefault(name, "").strip());
        }
        FormElement tree = siteForm(values);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(values));
        if (!ZoneId.getAvailableZoneIds().contains(values.get(TIMEZONE))) {
            state.error(TIMEZONE, "Choose a timezone.");
        }
        if (languages.find(values.get(LANGUAGE)).isEmpty()) {
            state.error(LANGUAGE, "Choose a language.");
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        config.save(SiteInformation.CONFIG_NAME, new SiteInformation(values.get(NAME), values.get(SLOGAN),
                values.get(MAIL), site().url(), values.get(FRONT_PAGE)));
        config.save(ErrorPagesConfig.CONFIG_NAME, new ErrorPagesConfig(values.get(FORBIDDEN_PAGE),
                values.get(NOT_FOUND_PAGE)));
        config.save(SiteDateSettings.CONFIG_NAME, config.read(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class,
                SiteDateSettings.DEFAULTS).withDefaultTimezone(values.get(TIMEZONE)));
        languages.makeDefault(values.get(LANGUAGE));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    private SiteInformation site() {
        return config.read(SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
    }

    private FormElement siteForm(Map<String, String> values) {
        ValidationRule sitePath = ValidationRule.pattern(PATH_PATTERN).withMessage("Enter a path starting with /.");
        return FormElement.of(ElementType.CONTAINER, "site-information")
                .child(FormElement.of(ElementType.FIELDSET, "site-details").label("Site details")
                        .child(FormElement.of(ElementType.TEXTFIELD, NAME).label("Site name")
                                .value(values.get(NAME)).markRequired().rule(ValidationRule.maxLength(128)))
                        .child(FormElement.of(ElementType.TEXTFIELD, SLOGAN).label("Slogan")
                                .description("Shown beside the site name, where the theme shows it.")
                                .value(values.get(SLOGAN)).rule(ValidationRule.maxLength(255)))
                        .child(FormElement.of(ElementType.TEXTFIELD, MAIL).label("Email address")
                                .description("The address the site's mail is sent from.")
                                .value(values.get(MAIL)).markRequired().rule(ValidationRule.maxLength(254))
                                .rule(ValidationRule.email())))
                .child(FormElement.of(ElementType.FIELDSET, "front-page").label("Front page")
                        .child(FormElement.of(ElementType.TEXTFIELD, FRONT_PAGE).label("Default front page")
                                .description("The path the home page shows. Blank shows the front page listing, "
                                        + SiteInformation.DEFAULT_FRONT_PAGE + ".")
                                .value(values.get(FRONT_PAGE)).rule(ValidationRule.maxLength(255)).rule(sitePath)))
                .child(FormElement.of(ElementType.FIELDSET, "error-pages").label("Error pages")
                        .child(FormElement.of(ElementType.TEXTFIELD, FORBIDDEN_PAGE).label("Default 403 page")
                                .description("The path shown when access is refused. Blank shows the default page.")
                                .value(values.get(FORBIDDEN_PAGE)).rule(ValidationRule.maxLength(255)).rule(sitePath))
                        .child(FormElement.of(ElementType.TEXTFIELD, NOT_FOUND_PAGE).label("Default 404 page")
                                .description("The path shown when nothing is found. Blank shows the default page.")
                                .value(values.get(NOT_FOUND_PAGE)).rule(ValidationRule.maxLength(255))
                                .rule(sitePath)))
                .child(FormElement.of(ElementType.FIELDSET, "locale").label("Locale")
                        .child(FormElement.of(ElementType.SELECT, TIMEZONE).label("Default timezone")
                                .value(values.get(TIMEZONE)).markRequired()
                                .options(ZoneId.getAvailableZoneIds().stream().sorted()
                                        .map(zone -> new SelectOption(zone, zone)).toList()))
                        .child(FormElement.of(ElementType.SELECT, LANGUAGE).label("Default language")
                                .value(values.get(LANGUAGE)).markRequired()
                                .options(languages.all().stream()
                                        .map(language -> new SelectOption(language.id(), language.label())).toList())))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Basic site settings");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/settings-form";
    }
}
