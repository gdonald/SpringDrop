package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.language.LanguageNegotiator;
import dev.springdrop.kernel.language.NegotiationSettings;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The languages the site is shown in, and how a request's language is chosen:
 * which negotiation methods are on, in what order, and whether the URL method
 * reads a path prefix or the domain.
 */
@Controller
public class LanguageController {

    public static final String PATH = "/admin/config/regional/language";

    public static final String ADD_PATH = PATH + "/add";

    public static final String DETECTION_PATH = PATH + "/detection";

    public static final String LANGCODE = "langcode";

    public static final String LABEL = "label";

    public static final String DIRECTION = "direction";

    public static final String WEIGHT = "weight";

    public static final String PREFIX = "prefix";

    public static final String DOMAIN = "domain";

    public static final String DEFAULT = "default";

    public static final String URL_SOURCE = "url_source";

    static final String PREFIX_PATTERN = "[a-z0-9-]{0,32}";

    static final String DOMAIN_PATTERN = "([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(:\\d{1,5})?";

    static final String WEIGHT_PATTERN = "-?\\d{1,4}";

    private static final Map<String, String> METHOD_LABELS = Map.of(
            NegotiationSettings.URL, "URL (prefix or domain)",
            NegotiationSettings.USER, "Account preference",
            NegotiationSettings.SESSION, "Session (the language parameter)");

    private final LanguageManager languages;
    private final LanguageNegotiator negotiator;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public LanguageController(LanguageManager languages, LanguageNegotiator negotiator, FormBuilder formBuilder,
            FormRenderer renderer) {
        this.languages = languages;
        this.negotiator = negotiator;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String langcode) {
        return PATH + "/manage/" + langcode;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Languages");
        model.addAttribute("description", "The languages the site is shown in.");
        model.addAttribute("languages", languages.all());
        model.addAttribute("defaultLangcode", languages.defaultLangcode());
        model.addAttribute("addPath", ADD_PATH);
        model.addAttribute("detectionPath", DETECTION_PATH);
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/languages";
    }

    @GetMapping(ADD_PATH)
    public String addForm(Model model) {
        return formPage("Add language", ADD_PATH, languageForm(Map.of(DIRECTION, LanguageConfig.LTR, WEIGHT, "0"),
                true), Map.of(), model);
    }

    @PostMapping(ADD_PATH)
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        return save(Optional.empty(), submitted, "Add language", ADD_PATH, model);
    }

    @GetMapping(PATH + "/manage/{langcode}")
    public String editForm(@PathVariable String langcode, Model model) {
        LanguageConfig language = languageOf(langcode);
        Map<String, String> values = new LinkedHashMap<>();
        values.put(LANGCODE, language.id());
        values.put(LABEL, language.label());
        values.put(DIRECTION, language.direction());
        values.put(WEIGHT, String.valueOf(language.weight()));
        values.put(PREFIX, language.prefix());
        values.put(DOMAIN, language.domain());
        values.put(DEFAULT, language.id().equals(languages.defaultLangcode()) ? FormRenderer.CHECKED_VALUE : "");
        return formPage("Edit " + language.label(), managePath(langcode), languageForm(values, false), Map.of(),
                model);
    }

    @PostMapping(PATH + "/manage/{langcode}")
    public String edit(@PathVariable String langcode, @RequestParam Map<String, String> submitted, Model model) {
        LanguageConfig language = languageOf(langcode);
        return save(Optional.of(language), submitted, "Edit " + language.label(), managePath(langcode), model);
    }

    @GetMapping(PATH + "/manage/{langcode}/delete")
    public String confirmDelete(@PathVariable String langcode, Model model) {
        LanguageConfig language = deletableLanguageOf(langcode);
        model.addAttribute("title", "Delete the language " + language.label() + "?");
        model.addAttribute("description", "This action cannot be undone.");
        model.addAttribute("action", managePath(langcode) + "/delete");
        model.addAttribute("confirmLabel", "Delete language");
        model.addAttribute("cancelPath", PATH);
        return "admin/confirm";
    }

    @PostMapping(PATH + "/manage/{langcode}/delete")
    public String delete(@PathVariable String langcode) {
        languages.delete(deletableLanguageOf(langcode).id());
        return "redirect:" + PATH;
    }

    @GetMapping(DETECTION_PATH)
    public String detectionForm(Model model) {
        NegotiationSettings settings = negotiator.settings();
        Map<String, String> values = new LinkedHashMap<>();
        for (String method : NegotiationSettings.ALL_METHODS) {
            int position = settings.methods().indexOf(method);
            values.put(method, position >= 0 ? FormRenderer.CHECKED_VALUE : "");
            values.put(weightName(method), String.valueOf(position >= 0 ? position : NegotiationSettings.ALL_METHODS
                    .size() + NegotiationSettings.ALL_METHODS.indexOf(method)));
        }
        values.put(URL_SOURCE, settings.urlSource());
        return detectionPage(detectionForm(values), Map.of(), model);
    }

    @PostMapping(DETECTION_PATH)
    public String detection(@RequestParam Map<String, String> submitted, Model model) {
        FormElement tree = detectionForm(submitted);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        String source = submitted.getOrDefault(URL_SOURCE, "");
        if (!source.isEmpty() && !source.equals(NegotiationSettings.PREFIX) && !source.equals(NegotiationSettings.DOMAIN)) {
            state.error(URL_SOURCE, "Choose where the URL names the language.");
        }
        if (state.hasErrors()) {
            return detectionPage(tree, state.errors(), model);
        }
        List<String> methods = NegotiationSettings.ALL_METHODS.stream()
                .filter(method -> FormRenderer.CHECKED_VALUE.equals(submitted.get(method)))
                .sorted(Comparator.comparingInt(method -> Integer.parseInt(submitted.get(weightName(method)))))
                .toList();
        negotiator.save(new NegotiationSettings(methods, source));
        return "redirect:" + DETECTION_PATH;
    }

    private String save(Optional<LanguageConfig> existing, Map<String, String> submitted, String title, String path,
            Model model) {
        Map<String, String> values = new LinkedHashMap<>(submitted);
        existing.ifPresent(language -> values.put(LANGCODE, language.id()));
        FormElement tree = languageForm(values, existing.isEmpty());
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(values));
        String langcode = values.getOrDefault(LANGCODE, "");
        String direction = values.getOrDefault(DIRECTION, "");
        String prefix = values.getOrDefault(PREFIX, "").strip();
        String domain = values.getOrDefault(DOMAIN, "").strip();
        if (existing.isEmpty() && languages.find(langcode).isPresent()) {
            state.error(LANGCODE, "The language " + langcode + " exists already.");
        }
        if (!direction.isEmpty() && !direction.equals(LanguageConfig.LTR) && !direction.equals(LanguageConfig.RTL)) {
            state.error(DIRECTION, "Choose a direction.");
        }
        List<LanguageConfig> others = languages.all().stream()
                .filter(language -> !language.id().equals(langcode)).toList();
        if (!prefix.isEmpty() && others.stream().anyMatch(language -> language.prefix().equals(prefix))) {
            state.error(PREFIX, "Another language uses the prefix " + prefix + ".");
        }
        if (!domain.isEmpty() && others.stream().anyMatch(language -> language.domain().equalsIgnoreCase(domain))) {
            state.error(DOMAIN, "Another language uses the domain " + domain + ".");
        }
        if (state.hasErrors()) {
            return formPage(title, path, tree, state.errors(), model);
        }
        languages.save(new LanguageConfig(langcode, values.get(LABEL).strip(), direction,
                Integer.parseInt(values.getOrDefault(WEIGHT, "0")), prefix, domain));
        if (FormRenderer.CHECKED_VALUE.equals(values.get(DEFAULT))) {
            languages.makeDefault(langcode);
        }
        return "redirect:" + PATH;
    }

    private static FormElement languageForm(Map<String, String> values, boolean adding) {
        FormElement code = adding
                ? FormElement.of(ElementType.TEXTFIELD, LANGCODE).label("Language code").markRequired()
                        .description("Two or three letters, and a region or script after a hyphen, such as fr or "
                                + "pt-br.")
                        .value(values.getOrDefault(LANGCODE, ""))
                        .rule(ValidationRule.pattern(LanguageManager.LANGCODE_PATTERN)
                                .withMessage("Enter a language code such as fr or pt-br."))
                : FormElement.of(ElementType.TEXT, LANGCODE).label("Language code: " + values.get(LANGCODE));
        return FormElement.of(ElementType.CONTAINER, "language-form")
                .child(code)
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired()
                        .value(values.getOrDefault(LABEL, ""))
                        .rule(ValidationRule.maxLength(64)))
                .child(FormElement.of(ElementType.SELECT, DIRECTION).label("Direction").markRequired()
                        .value(values.getOrDefault(DIRECTION, LanguageConfig.LTR))
                        .options(List.of(new SelectOption(LanguageConfig.LTR, "Left to right"),
                                new SelectOption(LanguageConfig.RTL, "Right to left"))))
                .child(FormElement.of(ElementType.TEXTFIELD, PREFIX).label("URL prefix")
                        .description("The path prefix that selects the language, such as fr for /fr/about. "
                                + "Blank for none.")
                        .value(values.getOrDefault(PREFIX, ""))
                        .rule(ValidationRule.pattern(PREFIX_PATTERN)
                                .withMessage("Use lowercase letters, digits, and hyphens.")))
                .child(FormElement.of(ElementType.TEXTFIELD, DOMAIN).label("Domain")
                        .description("The host name that selects the language, such as fr.example.com. "
                                + "Blank for none.")
                        .value(values.getOrDefault(DOMAIN, ""))
                        .rule(ValidationRule.pattern(DOMAIN_PATTERN).withMessage("Enter a host name.")))
                .child(FormElement.of(ElementType.NUMBER, WEIGHT).label("Weight")
                        .value(values.getOrDefault(WEIGHT, "0"))
                        .rule(ValidationRule.pattern(WEIGHT_PATTERN).withMessage("The weight is a whole number.")))
                .child(FormElement.of(ElementType.CHECKBOX, DEFAULT).label("Make this the default language")
                        .value(FormRenderer.CHECKED_VALUE.equals(values.get(DEFAULT))))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save language"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private static FormElement detectionForm(Map<String, String> values) {
        FormElement methods = FormElement.of(ElementType.FIELDSET, "methods").label("Detection methods");
        for (String method : NegotiationSettings.ALL_METHODS) {
            methods.child(FormElement.of(ElementType.CHECKBOX, method).label(METHOD_LABELS.get(method))
                            .value(FormRenderer.CHECKED_VALUE.equals(values.get(method))))
                    .child(FormElement.of(ElementType.NUMBER, weightName(method)).label("Order").markRequired()
                            .value(values.getOrDefault(weightName(method), "0"))
                            .rule(ValidationRule.pattern(WEIGHT_PATTERN)
                                    .withMessage("The order is a whole number.")));
        }
        return FormElement.of(ElementType.CONTAINER, "detection-form")
                .child(methods)
                .child(FormElement.of(ElementType.SELECT, URL_SOURCE).label("The URL names the language by")
                        .markRequired()
                        .value(values.getOrDefault(URL_SOURCE, NegotiationSettings.PREFIX))
                        .options(List.of(new SelectOption(NegotiationSettings.PREFIX, "Path prefix"),
                                new SelectOption(NegotiationSettings.DOMAIN, "Domain"))))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save settings")));
    }

    private static String weightName(String method) {
        return "weight_" + method;
    }

    private String formPage(String title, String path, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "A language the site is shown in, and the URL that selects it.");
        model.addAttribute("action", path);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private String detectionPage(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Language detection");
        model.addAttribute("description", "The methods tried in order, the lowest first. When none names a "
                + "language the site has, the default language is used.");
        model.addAttribute("action", DETECTION_PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private LanguageConfig languageOf(String langcode) {
        return languages.find(langcode).orElseThrow(() -> new EntityNotFoundException("language", langcode));
    }

    private LanguageConfig deletableLanguageOf(String langcode) {
        return languages.find(langcode).filter(language -> !language.id().equals(languages.defaultLangcode()))
                .orElseThrow(() -> new EntityNotFoundException("deletable language", langcode));
    }
}
