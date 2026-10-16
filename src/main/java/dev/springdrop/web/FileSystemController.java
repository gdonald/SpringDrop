package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileSystemProperties;
import dev.springdrop.kernel.file.FileSystemSettings;
import dev.springdrop.kernel.file.UploadSchemes;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
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
 * The file system settings: where public, private, and temporary files are
 * kept, which the deployment sets and the form shows, and the default
 * download method new uploads follow.
 */
@Controller
public class FileSystemController {

    public static final String PATH = "/admin/config/media/file-system";

    public static final String DEFAULT_SCHEME = "file_default_scheme";

    private static final String READONLY = "readonly";

    static final List<SelectOption> METHODS = List.of(
            new SelectOption(FileSchemes.PUBLIC, "Public local files served by the web server"),
            new SelectOption(FileSchemes.PRIVATE, "Private local files served through access checks"));

    private final ConfigStore config;
    private final UploadSchemes uploadSchemes;
    private final FileSystemProperties paths;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final Messenger messenger;

    public FileSystemController(ConfigStore config, UploadSchemes uploadSchemes, FileSystemProperties paths,
            FormBuilder formBuilder, FormRenderer renderer, Messenger messenger) {
        this.messenger = messenger;
        this.config = config;
        this.uploadSchemes = uploadSchemes;
        this.paths = paths;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        return page(fileSystemForm(uploadSchemes.defaultScheme()), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String scheme = submitted.getOrDefault(DEFAULT_SCHEME, "");
        FormElement tree = fileSystemForm(scheme);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (METHODS.stream().noneMatch(method -> method.value().equals(scheme))) {
            state.error(DEFAULT_SCHEME, "Choose a download method.");
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        config.save(FileSystemSettings.CONFIG_NAME, new FileSystemSettings(scheme));
        messenger.success("The configuration options have been saved.");
        return "redirect:" + PATH;
    }

    private FormElement fileSystemForm(String scheme) {
        return FormElement.of(ElementType.CONTAINER, "file-system")
                .child(FormElement.of(ElementType.TEXTFIELD, "public_path").label("Public file system path")
                        .value(absolute(paths.publicPath())).attribute(READONLY, READONLY)
                        .description("Files here are served by the web server at " + FileSchemes.PUBLIC_URL_PREFIX
                                + ". Set by springdrop.files.public-path."))
                .child(FormElement.of(ElementType.TEXTFIELD, "private_path").label("Private file system path")
                        .value(absolute(paths.privatePath())).attribute(READONLY, READONLY)
                        .description("Files here are served only through access checks. Set by "
                                + "springdrop.files.private-path."))
                .child(FormElement.of(ElementType.TEXTFIELD, "temporary_path").label("Temporary directory")
                        .value(System.getProperty("java.io.tmpdir")).attribute(READONLY, READONLY)
                        .description("Where uploads are held while they arrive. Set by the Java runtime."))
                .child(FormElement.of(ElementType.RADIOS, DEFAULT_SCHEME).label("Default download method")
                        .description("Where a new upload is kept when its field names no place of its own.")
                        .value(scheme).markRequired().options(METHODS))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration")));
    }

    private static String absolute(String path) {
        return Path.of(path).toAbsolutePath().toString();
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "File system");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/settings-form";
    }
}
