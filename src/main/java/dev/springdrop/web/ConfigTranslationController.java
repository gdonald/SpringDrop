package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.language.ConfigTranslationManager;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Translating config: a list of the objects that can be translated, each
 * object's languages, and a form per language showing each source string with
 * a box for its translation. The source stays as it is.
 */
@Controller
public class ConfigTranslationController {

    public static final String PATH = "/admin/config/regional/config-translation";

    private final ConfigTranslationManager translations;
    private final LanguageManager languages;
    private final FormRenderer renderer;

    public ConfigTranslationController(ConfigTranslationManager translations, LanguageManager languages,
            FormRenderer renderer) {
        this.translations = translations;
        this.languages = languages;
        this.renderer = renderer;
    }

    public static String itemPath(String configName) {
        return PATH + "/" + configName;
    }

    public static String translatePath(String configName, String langcode) {
        return itemPath(configName) + "/" + langcode;
    }

    /** One language of an object: whether it is translated into it. */
    public record LanguageRow(LanguageConfig language, boolean translated) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Configuration translation");
        model.addAttribute("items", translations.items());
        model.addAttribute("itemPath", PATH + "/");
        return "admin/config-translation";
    }

    @GetMapping(PATH + "/{name}")
    public String item(@PathVariable String name, Model model) {
        ConfigTranslationManager.Item item = itemOf(name);
        model.addAttribute("title", "Translations of " + item.label());
        model.addAttribute("languages", translatable().stream()
                .map(language -> new LanguageRow(language, !translations.translation(language.id(), name).isEmpty()))
                .toList());
        model.addAttribute("translatePath", itemPath(name) + "/");
        return "admin/config-translation-item";
    }

    @GetMapping(PATH + "/{name}/{langcode}")
    public String form(@PathVariable String name, @PathVariable String langcode, Model model) {
        ConfigTranslationManager.Item item = itemOf(name);
        LanguageConfig language = languageOf(langcode);
        Map<String, String> translated = translations.translation(langcode, name);
        FormElement form = FormElement.of(ElementType.CONTAINER, "config-translation");
        List<ConfigTranslationManager.SourceString> strings = translations.sourceStrings(name);
        for (int index = 0; index < strings.size(); index++) {
            ConfigTranslationManager.SourceString string = strings.get(index);
            form.child(FormElement.of(ElementType.FIELDSET, "string-" + index).label(string.label())
                    .child(FormElement.of(ElementType.TEXT, "source-" + index).label(string.text())
                            .attribute("class", "text-body-secondary config-source"))
                    .child(FormElement.of(ElementType.TEXTAREA, fieldName(index))
                            .label(language.label() + " translation")
                            .value(translated.getOrDefault(string.path(), ""))));
        }
        form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save translation"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(itemPath(name))));
        model.addAttribute("title", "Translate " + item.label() + " into " + language.label());
        model.addAttribute("description", "Leave a box empty to show the source.");
        model.addAttribute("action", translatePath(name, langcode));
        model.addAttribute("formMarkup", renderer.render(form));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/{name}/{langcode}")
    public String save(@PathVariable String name, @PathVariable String langcode,
            @RequestParam Map<String, String> submitted) {
        itemOf(name);
        languageOf(langcode);
        Map<String, String> values = new LinkedHashMap<>();
        List<ConfigTranslationManager.SourceString> strings = translations.sourceStrings(name);
        for (int index = 0; index < strings.size(); index++) {
            values.put(strings.get(index).path(), submitted.getOrDefault(fieldName(index), ""));
        }
        translations.save(langcode, name, values);
        return "redirect:" + itemPath(name);
    }

    private static String fieldName(int index) {
        return "translation_" + index;
    }

    private ConfigTranslationManager.Item itemOf(String name) {
        return translations.items().stream().filter(item -> item.name().equals(name)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("translatable config", name));
    }

    private LanguageConfig languageOf(String langcode) {
        return translatable().stream().filter(language -> language.id().equals(langcode)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("translatable language", langcode));
    }

    private List<LanguageConfig> translatable() {
        return languages.all().stream().filter(language -> !language.id().equals(LanguageManager.ENGLISH)).toList();
    }
}
