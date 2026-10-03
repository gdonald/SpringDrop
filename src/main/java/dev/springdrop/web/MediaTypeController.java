package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.Comparator;
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
 * The site's media types. Adding one chooses its media source, which cannot be
 * changed after, and creates the field holding each item's source value. A
 * type with media cannot be deleted.
 */
@Controller
public class MediaTypeController {

    public static final String PATH = FieldUiController.PATH_PREFIX + "/" + MediaEntityType.ID;

    public static final String LABEL = "label";

    public static final String DESCRIPTION = "description";

    public static final String SOURCE = "source";

    private final MediaTypeManager types;
    private final MediaService media;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public MediaTypeController(MediaTypeManager types, MediaService media, MachineNameGenerator machineNames,
            FormBuilder formBuilder, FormRenderer renderer) {
        this.types = types;
        this.media = media;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    public record MediaTypeRow(String id, String label, String description, String source, String fieldsPath) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Media types");
        model.addAttribute("description", "The kinds of media the site holds, each drawn from one source.");
        model.addAttribute("rows", types.all().stream().map(type -> new MediaTypeRow(type.id(), type.label(),
                type.description(), media.sources().has(type.source()) ? media.sources().get(type.source()).label()
                        : type.source(),
                FieldUiController.PATH_PREFIX + "/" + MediaEntityType.ID + "/" + type.id() + "/fields")).toList());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/media-types";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add media type", PATH + "/add", typeForm("", "", "", true), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "").strip();
        String source = submitted.getOrDefault(SOURCE, "");
        FormElement tree = typeForm(label, submitted.getOrDefault(DESCRIPTION, ""), source, true);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!source.isEmpty() && !media.sources().has(source)) {
            state.error(SOURCE, "Choose a media source.");
        }
        if (state.hasErrors()) {
            return formPage("Add media type", PATH + "/add", tree, state.errors(), model);
        }
        String id = machineNames.generateUnique(label, taken -> types.find(taken).isPresent());
        media.createType(id, label, submitted.getOrDefault(DESCRIPTION, "").strip(), source);
        return "redirect:" + PATH;
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        MediaType type = typeOf(id);
        return formPage("Edit " + type.label(), managePath(id),
                typeForm(type.label(), type.description(), type.source(), false), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        MediaType type = typeOf(id);
        String label = submitted.getOrDefault(LABEL, "").strip();
        FormElement tree = typeForm(label, submitted.getOrDefault(DESCRIPTION, ""), type.source(), false);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage("Edit " + type.label(), managePath(id), tree, state.errors(), model);
        }
        types.save(new MediaType(type.id(), label, submitted.getOrDefault(DESCRIPTION, "").strip(), type.source(),
                type.sourceField()));
        return "redirect:" + PATH;
    }

    private FormElement typeForm(String label, String description, String source, boolean adding) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "media-type")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired().value(label)
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.TEXTAREA, DESCRIPTION).label("Description").value(description)
                        .rule(ValidationRule.maxLength(1000)));
        if (adding) {
            form.child(FormElement.of(ElementType.SELECT, SOURCE).label("Media source").markRequired().value(source)
                    .description("Where items of this type come from. It cannot be changed later.")
                    .options(media.sources().ids().stream()
                            .map(id -> media.sources().get(id))
                            .sorted(Comparator.comparing(MediaSource::label))
                            .map(each -> new SelectOption(each.id(), each.label()))
                            .toList()));
        } else {
            form.child(FormElement.of(ElementType.TEXT, SOURCE).label("Media source: "
                    + (media.sources().has(source) ? media.sources().get(source).label() : source)));
        }
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        MediaType type = typeOf(id);
        boolean inUse = media.inUse(id);
        FormElement actions = FormElement.of(ElementType.ACTIONS, "actions");
        if (!inUse) {
            actions.child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete media type")
                    .attribute("class", "btn btn-danger"));
        }
        actions.child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH));
        model.addAttribute("title", "Delete the media type " + type.label() + "?");
        model.addAttribute("description", inUse
                ? "The site has media of this type. Delete them first."
                : "Its fields and displays are deleted with it. This action cannot be undone.");
        model.addAttribute("action", managePath(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(FormElement.of(ElementType.CONTAINER, "confirm")
                .child(actions)));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        MediaType type = typeOf(id);
        if (!media.inUse(id)) {
            media.deleteType(type);
        }
        return "redirect:" + PATH;
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "A media type names where its items come from.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private MediaType typeOf(String id) {
        return types.find(id).orElseThrow(() -> new EntityNotFoundException("media type", id));
    }
}
