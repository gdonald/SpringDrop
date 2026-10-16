package dev.springdrop.web;

import dev.springdrop.kernel.config.sync.ConfigFileException;
import dev.springdrop.kernel.config.sync.ConfigImportBatch;
import dev.springdrop.kernel.config.sync.ConfigSync;
import dev.springdrop.kernel.config.sync.ConfigSyncProperties;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.theme.Messenger;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Synchronizing configuration with the sync directory: what an import would
 * create, update, rename, and delete, the differences in one object, importing
 * every change through a batch, and exporting the site's config. An import
 * that would be refused lists why in place of the import button. A single
 * object can be exported, and imported from its value as YAML.
 */
@Controller
public class ConfigSyncController {

    public static final String PATH = "/admin/config/development/configuration";

    public static final String DIFF_PATH = PATH + "/diff";

    public static final String IMPORT_PATH = PATH + "/import";

    public static final String EXPORT_PATH = PATH + "/export";

    public static final String SINGLE_EXPORT_PATH = PATH + "/single/export";

    public static final String SINGLE_IMPORT_PATH = PATH + "/single/import";

    public static final String NAME = "name";

    public static final String YAML = "yaml";

    /** A config name: dot-separated parts of lowercase letters, digits, underscores, and hyphens. */
    static final String NAME_PATTERN = "[a-z0-9_-]+(\\.[a-z0-9_-]+)+";

    private final ConfigSync sync;
    private final ConfigImportBatch imports;
    private final ConfigSyncProperties properties;
    private final ConfigStore config;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public ConfigSyncController(ConfigSync sync, ConfigImportBatch imports, ConfigSyncProperties properties,
            ConfigStore config, FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.sync = sync;
        this.imports = imports;
        this.properties = properties;
        this.config = config;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String overview(Model model) {
        model.addAttribute("title", "Synchronize configuration");
        model.addAttribute("singleExportPath", SINGLE_EXPORT_PATH);
        model.addAttribute("singleImportPath", SINGLE_IMPORT_PATH);
        model.addAttribute("directory", directory().toAbsolutePath().toString());
        try {
            model.addAttribute("changes", sync.changes(directory()));
            model.addAttribute("problems", sync.problems(directory()));
        } catch (ConfigFileException unreadable) {
            model.addAttribute("changes", List.of());
            model.addAttribute("problems", List.of(unreadable.getMessage()));
        }
        model.addAttribute("importAction", IMPORT_PATH);
        model.addAttribute("exportAction", EXPORT_PATH);
        model.addAttribute("diffPath", DIFF_PATH);
        return "admin/config-sync";
    }

    @GetMapping(DIFF_PATH)
    public String diff(@RequestParam String name, Model model) {
        model.addAttribute("title", "Differences in " + name);
        model.addAttribute("active", sync.activeYaml(name).orElse(""));
        model.addAttribute("staged", sync.stagedYaml(directory(), name).orElse(""));
        model.addAttribute("back", PATH);
        return "admin/config-diff";
    }

    /** Starts the import batch, unless the import would be refused, which the overview then explains. */
    @PostMapping(IMPORT_PATH)
    public String importAll() {
        try {
            if (sync.problems(directory()).isEmpty()) {
                return "redirect:" + BatchController.pathFor(imports.start(directory(), PATH).batchId());
            }
        } catch (ConfigFileException unreadable) {
            // The overview names the file that cannot be read.
        }
        return "redirect:" + PATH;
    }

    @PostMapping(EXPORT_PATH)
    public String exportAll() {
        sync.export(directory());
        messenger.success("The configuration was exported to the sync directory.");
        return "redirect:" + PATH;
    }

    /** One object as an export writes it, chosen from every object the site has. */
    @GetMapping(SINGLE_EXPORT_PATH)
    public String singleExport(@RequestParam(name = NAME, defaultValue = "") String name, Model model) {
        model.addAttribute("title", "Export a single item");
        model.addAttribute("names", config.everything().keySet());
        model.addAttribute("chosen", name);
        model.addAttribute("document", sync.exportSingle(name).orElse(""));
        model.addAttribute("action", SINGLE_EXPORT_PATH);
        return "admin/config-single-export";
    }

    @GetMapping(SINGLE_IMPORT_PATH)
    public String singleImportForm(Model model) {
        return singleImportPage(singleImportForm("", ""), Map.of(), model);
    }

    @PostMapping(SINGLE_IMPORT_PATH)
    public String singleImport(@RequestParam Map<String, String> submitted, Model model) {
        String name = submitted.getOrDefault(NAME, "").strip();
        String yaml = submitted.getOrDefault(YAML, "");
        FormElement tree = singleImportForm(name, yaml);
        Map<String, Object> values = new LinkedHashMap<>(submitted);
        values.put(NAME, name);
        FormState state = formBuilder.validate(tree, values);
        if (!state.hasErrors()) {
            List<String> problems = sync.importSingle(name, yaml);
            if (problems.isEmpty()) {
                messenger.success(name + " was imported.");
                return "redirect:" + PATH;
            }
            state.error(YAML, String.join(" ", problems));
        }
        return singleImportPage(tree, state.errors(), model);
    }

    private static FormElement singleImportForm(String name, String yaml) {
        return FormElement.of(ElementType.CONTAINER, "config-single-import")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME).label("Configuration name")
                        .description("Such as user.role.editor.")
                        .value(name)
                        .markRequired()
                        .rule(ValidationRule.maxLength(255))
                        .rule(ValidationRule.pattern(NAME_PATTERN).withMessage(
                                "Enter dot-separated parts of lowercase letters, digits, underscores, and hyphens.")))
                .child(FormElement.of(ElementType.TEXTAREA, YAML).label("Value as YAML")
                        .description("The value only, as the data of an exported file.")
                        .value(yaml)
                        .markRequired())
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "import").label("Import")));
    }

    private String singleImportPage(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Import a single item");
        model.addAttribute("description", "The value is checked against its schema and dependencies first.");
        model.addAttribute("action", SINGLE_IMPORT_PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private Path directory() {
        return Path.of(properties.syncDirectory());
    }
}
