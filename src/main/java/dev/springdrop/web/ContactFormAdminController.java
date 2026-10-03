package dev.springdrop.web;

import dev.springdrop.kernel.contact.ContactEntityType;
import dev.springdrop.kernel.contact.ContactForm;
import dev.springdrop.kernel.contact.ContactFormManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.Arrays;
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
 * The site's contact forms: where each one's messages go, what the sender is
 * sent back, where they land afterwards, and which form {@code /contact}
 * shows. The personal form carries personal messages, so it has no
 * recipients to set and cannot be deleted, though its messages' fields are
 * managed like any other form's.
 */
@Controller
public class ContactFormAdminController {

    public static final String PATH = "/admin/structure/contact";

    public static final String LABEL = "label";

    public static final String RECIPIENTS = "recipients";

    public static final String REPLY = "reply";

    public static final String REDIRECT = "redirect";

    public static final String WEIGHT = "weight";

    public static final String SELECTED = "selected";

    /** One or more addresses, separated by commas. */
    static final String ADDRESS_LIST = "[^@\\s,]+@[^@\\s,]+\\.[^@\\s,]+(\\s*,\\s*[^@\\s,]+@[^@\\s,]+\\.[^@\\s,]+)*";

    /** A path on this site, or nothing. */
    static final String SITE_PATH = "/\\S*";

    private final ContactFormManager forms;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public ContactFormAdminController(
            ContactFormManager forms, MachineNameGenerator machineNames, FormBuilder formBuilder,
            FormRenderer renderer) {
        this.forms = forms;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Contact forms");
        model.addAttribute("description", "The forms people send the site messages through, and where they go.");
        model.addAttribute("forms", forms.all());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        model.addAttribute("fieldsPath", FieldUiController.PATH_PREFIX + "/" + ContactEntityType.ID + "/");
        return "admin/contact-forms";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add contact form", PATH + "/add", settingsForm(Map.of(), false), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "");
        String id = label.isBlank() ? "" : machineNames.generateUnique(label, taken -> forms.find(taken).isPresent());
        return save(id, false, submitted, "Add contact form", PATH + "/add", model);
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        ContactForm form = formOf(id);
        return formPage("Edit " + form.label(), managePath(id), settingsForm(Map.of(
                LABEL, form.label(),
                RECIPIENTS, String.join(", ", form.recipients()),
                REPLY, form.reply(),
                REDIRECT, form.redirect(),
                WEIGHT, String.valueOf(form.weight()),
                SELECTED, form.selected() ? "true" : ""), form.personal()), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        ContactForm form = formOf(id);
        return save(id, form.personal(), submitted, "Edit " + form.label(), managePath(id), model);
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        ContactForm form = deletableFormOf(id);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete contact form"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
        model.addAttribute("title", "Delete the contact form " + form.label() + "?");
        model.addAttribute("description", "This action cannot be undone.");
        model.addAttribute("action", managePath(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        deletableFormOf(id);
        forms.delete(id);
        return "redirect:" + PATH;
    }

    private String save(String id, boolean personal, Map<String, String> submitted, String title, String action,
            Model model) {
        FormElement tree = settingsForm(submitted, personal);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        String weight = submitted.getOrDefault(WEIGHT, "");
        forms.save(new ContactForm(id, submitted.get(LABEL), personal ? List.of() : recipientsOf(submitted),
                submitted.getOrDefault(REPLY, ""),
                personal ? "" : submitted.getOrDefault(REDIRECT, "").strip(),
                weight.matches(BlockLayoutController.WHOLE_NUMBER) ? Integer.parseInt(weight) : 0,
                !personal && submitted.containsKey(SELECTED)));
        return "redirect:" + PATH;
    }

    /** The recipients a submission names, which the form's rule has checked are addresses separated by commas. */
    private static List<String> recipientsOf(Map<String, String> submitted) {
        return Arrays.stream(submitted.getOrDefault(RECIPIENTS, "").split(",")).map(String::strip).toList();
    }

    /**
     * The form's settings. A site-wide form names its recipients, where the
     * sender lands, and whether {@code /contact} shows it. The personal form
     * has none of those.
     */
    private static FormElement settingsForm(Map<String, String> values, boolean personal) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "contact-form")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Label").markRequired()
                        .value(values.getOrDefault(LABEL, ""))
                        .rule(ValidationRule.maxLength(255)));
        if (!personal) {
            form.child(FormElement.of(ElementType.TEXTAREA, RECIPIENTS).label("Recipients").markRequired()
                    .description("Addresses the messages go to, separated by commas.")
                    .value(values.getOrDefault(RECIPIENTS, ""))
                    .rule(ValidationRule.pattern(ADDRESS_LIST)
                            .withMessage("Enter one or more email addresses, separated by commas.")));
        }
        form.child(FormElement.of(ElementType.TEXTAREA, REPLY).label("Auto-reply")
                .description("Sent to the sender. Left empty, nothing is sent.")
                .value(values.getOrDefault(REPLY, ""))
                .rule(ValidationRule.maxLength(ContactController.MESSAGE_MAX_LENGTH)));
        if (!personal) {
            form.child(FormElement.of(ElementType.TEXTFIELD, REDIRECT).label("Redirect path")
                            .description("Where the sender lands afterwards, a path on this site such as /thanks. "
                                    + "Left empty, the front page.")
                            .value(values.getOrDefault(REDIRECT, ""))
                            .rule(ValidationRule.pattern(SITE_PATH).withMessage("Enter a path starting with /.")))
                    .child(FormElement.of(ElementType.NUMBER, WEIGHT).label("Weight")
                            .value(values.getOrDefault(WEIGHT, "0"))
                            .rule(ValidationRule.pattern(BlockLayoutController.WHOLE_NUMBER)
                                    .withMessage("The weight is a whole number.")))
                    .child(FormElement.of(ElementType.CHECKBOX, SELECTED).label("Make this the default form")
                            .value(values.containsKey(SELECTED) && !values.get(SELECTED).isEmpty()));
        }
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save contact form"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors,
            Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "Where the form's messages go, and what the sender is sent back.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private ContactForm formOf(String id) {
        return forms.find(id).orElseThrow(() -> new EntityNotFoundException("contact form", id));
    }

    private ContactForm deletableFormOf(String id) {
        return forms.find(id).filter(form -> !form.personal())
                .orElseThrow(() -> new EntityNotFoundException("deletable contact form", id));
    }
}
