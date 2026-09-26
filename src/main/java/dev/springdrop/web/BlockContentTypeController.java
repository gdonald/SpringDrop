package dev.springdrop.web;

import dev.springdrop.kernel.block.content.BlockContentEntityType;
import dev.springdrop.kernel.block.content.BlockContentService;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The types custom blocks come in. Each carries the fields its blocks hold,
 * which are managed through the Field UI like any other bundle's. A type that
 * blocks in the library are still written in cannot be deleted.
 */
@Controller
public class BlockContentTypeController {

    public static final String PATH = "/admin/structure/block-content";

    public static final String LABEL = "label";

    private final BlockContentService library;
    private final MachineNameGenerator machineNames;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public BlockContentTypeController(
            BlockContentService library, MachineNameGenerator machineNames, FormBuilder forms, FormRenderer renderer) {
        this.library = library;
        this.machineNames = machineNames;
        this.forms = forms;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        return listPage(Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        FormState state = forms.validate(typeForm(), new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return listPage(state.errors(), model);
        }

        String label = submitted.get(LABEL);
        String id = machineNames.generateUnique(label, taken -> library.findType(taken).isPresent());
        library.saveType(new BundleDefinition(id, label));
        return "redirect:" + PATH;
    }

    @GetMapping(PATH + "/manage/{type}/delete")
    public String confirmDelete(@PathVariable String type, Model model) {
        BundleDefinition blockType = typeOf(type);
        boolean inUse = library.inUse(type);

        FormElement actions = FormElement.of(ElementType.ACTIONS, "actions");
        if (!inUse) {
            actions.child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete block type"));
        }
        actions.child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH));

        model.addAttribute("title", "Delete the block type " + blockType.label() + "?");
        model.addAttribute("description", inUse
                ? "This type cannot be deleted while custom blocks in the library are of it."
                : "This action cannot be undone.");
        model.addAttribute("action", PATH + "/manage/" + type + "/delete");
        model.addAttribute("formMarkup", renderer.render(
                FormElement.of(ElementType.CONTAINER, "confirm").child(actions)));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{type}/delete")
    public String delete(@PathVariable String type) {
        typeOf(type);
        if (library.inUse(type)) {
            return "redirect:" + PATH + "/manage/" + type + "/delete";
        }
        library.deleteType(type);
        return "redirect:" + PATH;
    }

    private String listPage(Map<String, String> errors, Model model) {
        model.addAttribute("title", "Block types");
        model.addAttribute("description", "The kinds of custom block the library holds, and the fields each carries.");
        model.addAttribute("types", library.types());
        model.addAttribute("fieldsPath", FieldUiController.PATH_PREFIX + "/" + BlockContentEntityType.ID + "/");
        model.addAttribute("managePath", PATH + "/manage/");
        model.addAttribute("action", PATH + "/add");
        model.addAttribute("formMarkup", renderer.render(typeForm(), errors));
        return "admin/block-content-types";
    }

    private static FormElement typeForm() {
        return FormElement.of(ElementType.CONTAINER, "block-type")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Block type name")
                        .markRequired()
                        .rule(ValidationRule.maxLength(64)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "add").label("Add block type")));
    }

    private BundleDefinition typeOf(String type) {
        return library.findType(type).orElseThrow(() -> new EntityNotFoundException("block type", type));
    }
}
