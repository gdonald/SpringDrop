package dev.springdrop.web;

import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.locale.PoFile;
import dev.springdrop.kernel.locale.TranslatableString;
import dev.springdrop.kernel.locale.Translator;
import dev.springdrop.kernel.theme.Pager;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Translating the interface: a page listing the strings the site has asked
 * for with a box for each one's translation into the chosen language, and
 * importing and exporting a language's translations as a {@code .po} file.
 * English is the source language, so only the other languages are offered.
 */
@Controller
public class TranslationController {

    public static final String PATH = "/admin/config/regional/translate";

    public static final String IMPORT_PATH = PATH + "/import";

    public static final String EXPORT_PATH = PATH + "/export";

    public static final String LANGCODE = "langcode";

    public static final String SEARCH = "search";

    public static final String UNTRANSLATED = "untranslated";

    public static final String PAGE = "page";

    public static final String IDS = "ids";

    public static final String FILE = "file";

    public static final int PAGE_SIZE = 50;

    public static final String CHOOSE_LANGUAGE = "Choose a language.";

    public static final String CHOOSE_FILE = "Choose a .po file.";

    private final Translator translator;
    private final LanguageManager languages;

    public TranslationController(Translator translator, LanguageManager languages) {
        this.translator = translator;
        this.languages = languages;
    }

    /** One string on the page: its source, its context, and a box for each of its translation's forms. */
    public record Row(long id, List<String> sources, String context, List<String> forms) {

        public String fieldName(int form) {
            return "translation_" + id + "_" + form;
        }
    }

    @GetMapping(PATH)
    public String translate(@RequestParam(name = LANGCODE, defaultValue = "") String langcode,
            @RequestParam(name = SEARCH, defaultValue = "") String search,
            @RequestParam(name = UNTRANSLATED, defaultValue = "") String untranslated,
            @RequestParam(name = PAGE, defaultValue = "1") String page, Model model) {
        List<LanguageConfig> translatable = translatable();
        model.addAttribute("title", "User interface translation");
        model.addAttribute("languages", translatable);
        model.addAttribute("importPath", IMPORT_PATH);
        model.addAttribute("exportPath", EXPORT_PATH);
        Optional<LanguageConfig> chosen = translatable.stream()
                .filter(language -> language.id().equals(langcode)).findFirst()
                .or(() -> translatable.stream().findFirst());
        model.addAttribute("chosen", chosen.map(LanguageConfig::id).orElse(""));
        model.addAttribute("search", search);
        model.addAttribute("untranslated", !untranslated.isEmpty());
        if (chosen.isEmpty()) {
            model.addAttribute("rows", List.of());
            model.addAttribute("pager", Pager.NONE);
            return "admin/translate";
        }
        String code = chosen.get().id();
        boolean onlyUntranslated = !untranslated.isEmpty();
        int total = translator.count(code, search, onlyUntranslated);
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = page.matches("\\d{1,6}") ? Math.min(Math.max(Integer.parseInt(page), 1), pages) : 1;
        model.addAttribute("rows", translator.strings(code, search, onlyUntranslated, (current - 1) * PAGE_SIZE,
                PAGE_SIZE).stream().map(TranslationController::row).toList());
        model.addAttribute("pager", pages > 1 ? Pager.of(current, pages, number -> UriComponentsBuilder
                .fromPath(PATH).queryParam(LANGCODE, code).queryParam(SEARCH, search)
                .queryParam(UNTRANSLATED, untranslated).queryParam(PAGE, number).build().encode().toUriString())
                : Pager.NONE);
        model.addAttribute("action", PATH);
        return "admin/translate";
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted) {
        String langcode = submitted.getOrDefault(LANGCODE, "");
        if (translatable().stream().noneMatch(language -> language.id().equals(langcode))) {
            throw new EntityNotFoundException("translatable language", langcode);
        }
        for (String id : submitted.getOrDefault(IDS, "").split(",")) {
            if (!id.matches("\\d{1,18}")) {
                continue;
            }
            translator.string(Long.parseLong(id), langcode).ifPresent(string -> {
                Row row = row(string);
                List<String> forms = new ArrayList<>();
                for (int form = 0; form < row.forms().size(); form++) {
                    forms.add(submitted.getOrDefault(row.fieldName(form), "").strip());
                }
                String translation = forms.stream().allMatch(String::isEmpty) ? ""
                        : String.join(Translator.PLURAL_DELIMITER, forms);
                if (!translation.equals(string.translation())) {
                    translator.save(langcode, string.source(), string.context(), translation);
                }
            });
        }
        return "redirect:" + UriComponentsBuilder.fromPath(PATH).queryParam(LANGCODE, langcode)
                .queryParam(SEARCH, submitted.getOrDefault(SEARCH, ""))
                .queryParam(PAGE, submitted.getOrDefault(PAGE, "1")).build().encode().toUriString();
    }

    @GetMapping(IMPORT_PATH)
    public String importForm(Model model) {
        return importPage(List.of(), model);
    }

    @PostMapping(IMPORT_PATH)
    public String importFile(@RequestParam(name = LANGCODE, defaultValue = "") String langcode,
            @RequestParam(name = FILE, required = false) MultipartFile file, Model model) throws IOException {
        List<String> errors = new ArrayList<>();
        if (translatable().stream().noneMatch(language -> language.id().equals(langcode))) {
            errors.add(CHOOSE_LANGUAGE);
        }
        if (file == null || file.isEmpty()) {
            errors.add(CHOOSE_FILE);
        }
        if (!errors.isEmpty()) {
            return importPage(errors, model);
        }
        List<PoFile.Entry> entries;
        try {
            entries = PoFile.read(new String(file.getBytes(), StandardCharsets.UTF_8));
        } catch (PoFile.MalformedException malformed) {
            return importPage(List.of("The file could not be read. " + malformed.getMessage()), model);
        }
        translator.importEntries(langcode, entries);
        return "redirect:" + PATH + "?" + LANGCODE + "=" + langcode;
    }

    @GetMapping(EXPORT_PATH)
    public String exportForm(Model model) {
        model.addAttribute("title", "Export translations");
        model.addAttribute("languages", translatable());
        model.addAttribute("exportPath", EXPORT_PATH + "/");
        return "admin/translation-export";
    }

    @GetMapping(EXPORT_PATH + "/{langcode}")
    public ResponseEntity<String> export(@PathVariable String langcode) {
        LanguageConfig language = translatable().stream().filter(each -> each.id().equals(langcode)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("translatable language", langcode));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "x-gettext-translation", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("springdrop." + langcode + ".po").build().toString())
                .body(PoFile.write(langcode, language.label(), translator.translated(langcode)));
    }

    private String importPage(List<String> errors, Model model) {
        model.addAttribute("title", "Import translations");
        model.addAttribute("languages", translatable());
        model.addAttribute("errors", errors);
        model.addAttribute("action", IMPORT_PATH);
        return "admin/translation-import";
    }

    /** Every language but English, the language the interface is written in. */
    private List<LanguageConfig> translatable() {
        return languages.all().stream().filter(language -> !language.id().equals(LanguageManager.ENGLISH)).toList();
    }

    private static Row row(TranslatableString string) {
        List<String> sources = Arrays.asList(string.source().split(Translator.PLURAL_DELIMITER, -1));
        List<String> forms = new ArrayList<>(Arrays.asList(string.translation().split(Translator.PLURAL_DELIMITER,
                -1)));
        while (forms.size() < sources.size()) {
            forms.add("");
        }
        return new Row(string.id(), sources, string.context(), forms);
    }
}
