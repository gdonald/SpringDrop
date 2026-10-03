package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
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
 * The content types nodes come in: what each is called, how its form introduces
 * it, and how a new node of it starts out. Each type's fields, form, and
 * displays are managed through the Field UI like any other bundle's. A type
 * that nodes are still written in cannot be deleted.
 */
@Controller
public class NodeTypeController {

    public static final String PATH = "/admin/structure/types";

    public static final String LABEL = "label";

    public static final String DESCRIPTION = "description";

    public static final String HELP = "help";

    public static final String TITLE_LABEL = "title_label";

    public static final String PUBLISHED = "published";

    public static final String PROMOTED = "promoted";

    public static final String STICKY = "sticky";

    public static final String NEW_REVISION = "new_revision";

    static final int LABEL_MAX_LENGTH = 64;

    static final int TITLE_LABEL_MAX_LENGTH = 255;

    static final int TEXT_MAX_LENGTH = 2048;

    private final NodeTypeManager types;
    private final NodeService nodes;
    private final MachineNameGenerator machineNames;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public NodeTypeController(
            NodeTypeManager types,
            NodeService nodes,
            MachineNameGenerator machineNames,
            FormBuilder forms,
            FormRenderer renderer) {
        this.types = types;
        this.nodes = nodes;
        this.machineNames = machineNames;
        this.forms = forms;
        this.renderer = renderer;
    }

    public static String managePath(String type) {
        return PATH + "/manage/" + type;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Content types");
        model.addAttribute("description", "The kinds of content the site holds, and the fields each carries.");
        model.addAttribute("types", types.all());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        model.addAttribute("fieldsPath", FieldUiController.PATH_PREFIX + "/" + NodeEntityType.ID + "/");
        return "admin/node-types";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add content type", PATH + "/add", typeForm(NodeType.of("", "")), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "");
        String id = label.isBlank() ? ""
                : machineNames.generateUnique(label, taken -> types.find(taken).isPresent());
        return save(fromSubmission(id, submitted), submitted, "Add content type", PATH + "/add", model);
    }

    @GetMapping(PATH + "/manage/{type}")
    public String editForm(@PathVariable String type, Model model) {
        NodeType contentType = typeOf(type);
        return formPage("Edit " + contentType.label(), managePath(type), typeForm(contentType), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{type}")
    public String edit(@PathVariable String type, @RequestParam Map<String, String> submitted, Model model) {
        NodeType contentType = typeOf(type);
        return save(fromSubmission(type, submitted), submitted, "Edit " + contentType.label(),
                managePath(type), model);
    }

    @GetMapping(PATH + "/manage/{type}/delete")
    public String confirmDelete(@PathVariable String type, Model model) {
        NodeType contentType = typeOf(type);
        boolean inUse = nodes.inUse(type);

        FormElement actions = FormElement.of(ElementType.ACTIONS, "actions");
        if (!inUse) {
            actions.child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete content type"));
        }
        actions.child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH));

        model.addAttribute("title", "Delete the content type " + contentType.label() + "?");
        model.addAttribute("description", inUse
                ? "This type cannot be deleted while content of it exists."
                : "This action cannot be undone.");
        model.addAttribute("action", managePath(type) + "/delete");
        model.addAttribute("formMarkup", renderer.render(
                FormElement.of(ElementType.CONTAINER, "confirm").child(actions)));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{type}/delete")
    public String delete(@PathVariable String type) {
        typeOf(type);
        if (nodes.inUse(type)) {
            return "redirect:" + managePath(type) + "/delete";
        }
        types.delete(type);
        return "redirect:" + PATH;
    }

    /**
     * Saves the type once the form's rules pass, then sends the admin on to its
     * fields. Otherwise the form comes back holding what was submitted, with
     * each error on its field.
     */
    private String save(
            NodeType type, Map<String, String> submitted, String title, String action, Model model) {

        FormElement tree = typeForm(type);
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        types.save(type);
        return "redirect:" + FieldUiController.fieldsPath(NodeEntityType.ID, type.id());
    }

    private static NodeType fromSubmission(String id, Map<String, String> submitted) {
        return NodeType.of(id, submitted.getOrDefault(LABEL, ""))
                .describedAs(submitted.getOrDefault(DESCRIPTION, ""))
                .withHelp(submitted.getOrDefault(HELP, ""))
                .withTitleLabel(submitted.getOrDefault(TITLE_LABEL, ""))
                .withDefaults(
                        submitted.containsKey(PUBLISHED),
                        submitted.containsKey(PROMOTED),
                        submitted.containsKey(STICKY),
                        submitted.containsKey(NEW_REVISION));
    }

    private static FormElement typeForm(NodeType type) {
        return FormElement.of(ElementType.CONTAINER, "content-type")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Name")
                        .description("The name the admin and the add-content page list this type as.")
                        .markRequired()
                        .value(type.label())
                        .rule(ValidationRule.maxLength(LABEL_MAX_LENGTH)))
                .child(FormElement.of(ElementType.TEXTAREA, DESCRIPTION)
                        .label("Description")
                        .description("Shown on the add-content page next to the type's name.")
                        .value(type.description())
                        .rule(ValidationRule.maxLength(TEXT_MAX_LENGTH)))
                .child(FormElement.of(ElementType.TEXTAREA, HELP)
                        .label("Explanation or submission guidelines")
                        .description("Shown at the top of the form for adding and editing content of this type.")
                        .value(type.help())
                        .rule(ValidationRule.maxLength(TEXT_MAX_LENGTH)))
                .child(FormElement.of(ElementType.TEXTFIELD, TITLE_LABEL)
                        .label("Title field label")
                        .markRequired()
                        .value(type.titleLabel())
                        .rule(ValidationRule.maxLength(TITLE_LABEL_MAX_LENGTH)))
                .child(FormElement.of(ElementType.DETAILS, "publishing")
                        .label("Publishing options")
                        .description("How a new node of this type starts out.")
                        .child(FormElement.of(ElementType.CHECKBOX, PUBLISHED)
                                .label("Published").value(type.published()))
                        .child(FormElement.of(ElementType.CHECKBOX, PROMOTED)
                                .label("Promoted to front page").value(type.promoted()))
                        .child(FormElement.of(ElementType.CHECKBOX, STICKY)
                                .label("Sticky at top of lists").value(type.sticky()))
                        .child(FormElement.of(ElementType.CHECKBOX, NEW_REVISION)
                                .label("Create new revision").value(type.newRevision())))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save content type"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private String formPage(
            String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "A kind of content, with the fields its nodes carry.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private NodeType typeOf(String type) {
        return types.find(type).orElseThrow(() -> new EntityNotFoundException("content type", type));
    }
}
