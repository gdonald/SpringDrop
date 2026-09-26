package dev.springdrop.web;

import dev.springdrop.kernel.block.content.BlockContentEntityType;
import dev.springdrop.kernel.block.content.BlockContentService;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.field.FieldConstraintProvider;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.validation.ConstraintViolation;
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
 * The custom block library: the reusable blocks a site writes once and places
 * wherever it wants them. Each is written with the fields its block type
 * carries, and every save keeps a revision.
 */
@Controller
public class CustomBlockController {

    public static final String PATH = "/admin/content/block";

    public static final String INFO = "info";

    private final BlockContentService library;
    private final FormDisplayManager formDisplays;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public CustomBlockController(
            BlockContentService library,
            FormDisplayManager formDisplays,
            FormBuilder forms,
            FormRenderer renderer) {
        this.library = library;
        this.formDisplays = formDisplays;
        this.forms = forms;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        Map<String, String> typeLabels = new LinkedHashMap<>();
        library.types().forEach(type -> typeLabels.put(type.id(), type.label()));

        model.addAttribute("title", "Custom block library");
        model.addAttribute("description", "Blocks written once and placed wherever the site wants them.");
        model.addAttribute("types", library.types());
        model.addAttribute("typeLabels", typeLabels);
        model.addAttribute("blocks", library.all());
        model.addAttribute("addPath", PATH + "/add/");
        model.addAttribute("blockPath", PATH + "/");
        return "admin/custom-blocks";
    }

    @GetMapping(PATH + "/add/{type}")
    public String addForm(@PathVariable String type, Model model) {
        BundleDefinition blockType = typeOf(type);
        EntityData blank = EntityData.of(BlockContentEntityType.ID, null, type, "", Map.of());
        return formPage("Add " + blockType.label() + " block", PATH + "/add/" + type,
                blockForm(blank), Map.of(), model);
    }

    @PostMapping(PATH + "/add/{type}")
    public String add(@PathVariable String type, @RequestParam Map<String, String> submitted, Model model) {
        BundleDefinition blockType = typeOf(type);
        EntityData blank = EntityData.of(BlockContentEntityType.ID, null, type, "", Map.of());
        return save(blank, submitted, "Add " + blockType.label() + " block", PATH + "/add/" + type, model);
    }

    @GetMapping(PATH + "/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        EntityData block = blockOf(id);
        return formPage("Edit " + block.label(), PATH + "/" + id + "/edit", blockForm(block), Map.of(), model);
    }

    @PostMapping(PATH + "/{id}/edit")
    public String edit(@PathVariable long id, @RequestParam Map<String, String> submitted, Model model) {
        EntityData block = blockOf(id);
        return save(block, submitted, "Edit " + block.label(), PATH + "/" + id + "/edit", model);
    }

    @GetMapping(PATH + "/{id}/delete")
    public String confirmDelete(@PathVariable long id, Model model) {
        EntityData block = blockOf(id);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete custom block"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
        model.addAttribute("title", "Delete the custom block " + block.label() + "?");
        model.addAttribute("description",
                "Wherever it is placed, the placement stays and shows nothing. This action cannot be undone.");
        model.addAttribute("action", PATH + "/" + id + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/{id}/delete")
    public String delete(@PathVariable long id) {
        blockOf(id);
        library.delete(id);
        return "redirect:" + PATH;
    }

    /**
     * Saves the block once the form's rules and the entity's own constraints
     * pass. Otherwise the form comes back holding what was submitted, with each
     * error on its field.
     */
    private String save(
            EntityData base, Map<String, String> submitted, String title, String action, Model model) {

        Map<String, Object> values = new LinkedHashMap<>(submitted);
        Map<String, Object> fields = formDisplays.extract(
                BlockContentEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE, values);
        EntityData candidate = new EntityData(BlockContentEntityType.ID, base.id(), base.uuid(), base.bundle(),
                submitted.getOrDefault(INFO, ""), EntityData.DEFAULT_LANGCODE, null, fields);
        FormElement tree = blockForm(candidate);

        FormState state = forms.validate(tree, values);
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        try {
            library.save(candidate);
        } catch (EntityValidationException invalid) {
            Map<String, String> errors = new LinkedHashMap<>();
            invalid.violations().forEach(violation -> errors.putIfAbsent(elementOf(violation), violation.message()));
            return formPage(title, action, tree, errors, model);
        }
        return "redirect:" + PATH;
    }

    /** The field a violation belongs to, which its path names after the field prefix. */
    private static String elementOf(ConstraintViolation violation) {
        return violation.propertyPath().substring(FieldConstraintProvider.FIELD_PATH_PREFIX.length());
    }

    private FormElement blockForm(EntityData block) {
        return FormElement.of(ElementType.CONTAINER, "custom-block")
                .child(FormElement.of(ElementType.TEXTFIELD, INFO)
                        .label("Block description")
                        .description("What the library lists this block as. Visitors never see it.")
                        .markRequired()
                        .value(block.label())
                        .rule(ValidationRule.maxLength(255)))
                .child(formDisplays.buildContainer(
                        BlockContentEntityType.ID, block.bundle(), FormDisplayConfig.DEFAULT_MODE, block.fields()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save custom block"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private String formPage(
            String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "A reusable block, placed from the block layout.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private BundleDefinition typeOf(String type) {
        return library.findType(type).orElseThrow(() -> new EntityNotFoundException("block type", type));
    }

    private EntityData blockOf(long id) {
        return library.find(id).orElseThrow(() -> new EntityNotFoundException("custom block", String.valueOf(id)));
    }
}
