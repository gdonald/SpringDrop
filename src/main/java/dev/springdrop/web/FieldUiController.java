package dev.springdrop.web;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * The fields of one bundle: what it has, adding another, changing one, and
 * removing one. Adding runs in two steps, since the second step's settings
 * depend on the type chosen in the first.
 */
@Controller
public class FieldUiController {

    public static final String PATH_PREFIX = "/admin/structure";

    public static final String FIELD_NAME = "field_name";

    public static final String FIELD_TYPE = "field_type";

    public static final String LABEL = "label";

    public static final String CARDINALITY = "cardinality";

    public static final String REQUIRED = "required";

    public static final String DESCRIPTION = "description";

    public static final String DEFAULT_VALUE = "default_value";

    /** Which step of the add flow a submission came from. */
    public static final String STEP = "step";

    private final FieldConfigManager fields;
    private final FormRenderer renderer;
    private final PluginRegistry pluginRegistry;

    public FieldUiController(
            FieldConfigManager fields, FormRenderer renderer, PluginRegistry pluginRegistry) {
        this.fields = fields;
        this.renderer = renderer;
        this.pluginRegistry = pluginRegistry;
    }

    public static String fieldsPath(String entityTypeId, String bundle) {
        return PATH_PREFIX + "/" + entityTypeId + "/" + bundle + "/fields";
    }

    @GetMapping(PATH_PREFIX + "/{entityType}/{bundle}/fields")
    public String list(@PathVariable String entityType, @PathVariable String bundle, Model model) {
        model.addAttribute("title", "Manage fields");
        model.addAttribute("description", "The fields entities of this bundle carry.");
        model.addAttribute("listing", listing(entityType, bundle));
        model.addAttribute("action", fieldsPath(entityType, bundle) + "/add");
        model.addAttribute("formMarkup", renderer.render(chooseTypeForm()));
        return "admin/field-ui";
    }

    /**
     * Step one names the field and its type; step two asks for the settings that
     * type and its storage need, and creates both config objects.
     */
    @PostMapping(PATH_PREFIX + "/{entityType}/{bundle}/fields/add")
    public String add(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @RequestParam Map<String, String> submitted,
            Model model) {

        if (!"2".equals(submitted.get(STEP))) {
            model.addAttribute("title", "Add field");
            model.addAttribute("description", "Settings for a "
                    + submitted.getOrDefault(FIELD_TYPE, "") + " field.");
            model.addAttribute("action", fieldsPath(entityType, bundle) + "/add");
            model.addAttribute("formMarkup", renderer.render(settingsForm(submitted)));
            return "admin/field-ui";
        }

        String fieldName = submitted.get(FIELD_NAME);
        fields.createStorage(new FieldStorageConfig(
                fieldName, entityType, submitted.get(FIELD_TYPE), cardinality(submitted), Map.of()));
        fields.createInstance(instance(entityType, bundle, fieldName, submitted));
        return "redirect:" + fieldsPath(entityType, bundle);
    }

    @GetMapping(PATH_PREFIX + "/{entityType}/{bundle}/fields/{field}")
    public String edit(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @PathVariable String field,
            Model model) {

        FieldStorageConfig storage = fields.findStorage(entityType, field).orElseThrow();
        FieldInstanceConfig instance = fields.findInstance(entityType, bundle, field).orElseThrow();

        model.addAttribute("title", "Edit " + instance.label());
        model.addAttribute("description", "A " + storage.type() + " field.");
        model.addAttribute("action", fieldsPath(entityType, bundle) + "/" + field);
        model.addAttribute("formMarkup", renderer.render(editForm(storage, instance)));
        return "admin/field-ui";
    }

    @PostMapping(PATH_PREFIX + "/{entityType}/{bundle}/fields/{field}")
    public String save(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @PathVariable String field,
            @RequestParam Map<String, String> submitted) {

        FieldStorageConfig storage = fields.findStorage(entityType, field).orElseThrow();
        fields.createStorage(new FieldStorageConfig(
                field, entityType, storage.type(), cardinality(submitted), storage.settings()));
        fields.createInstance(instance(entityType, bundle, field, submitted));
        return "redirect:" + fieldsPath(entityType, bundle);
    }

    @GetMapping(PATH_PREFIX + "/{entityType}/{bundle}/fields/{field}/delete")
    public String confirmDelete(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @PathVariable String field,
            Model model) {

        FieldInstanceConfig instance = fields.findInstance(entityType, bundle, field).orElseThrow();

        model.addAttribute("title", "Delete the field " + instance.label() + "?");
        model.addAttribute("description", "Everything stored in it goes with it.");
        model.addAttribute("action", fieldsPath(entityType, bundle) + "/" + field + "/delete");
        model.addAttribute("formMarkup", renderer.render(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete"))
                .child(FormElement.of(ElementType.LINK, "cancel")
                        .label("Cancel")
                        .value(fieldsPath(entityType, bundle)))));
        return "admin/field-ui";
    }

    @PostMapping(PATH_PREFIX + "/{entityType}/{bundle}/fields/{field}/delete")
    public String delete(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @PathVariable String field) {

        fields.deleteStorage(entityType, field);
        return "redirect:" + fieldsPath(entityType, bundle);
    }

    /** The bundle's fields, each with an Edit and a Delete button. */
    private String listing(String entityTypeId, String bundle) {
        StringBuilder markup = new StringBuilder(
                "<table class=\"table\"><thead><tr>"
                        + "<th scope=\"col\">Label</th><th scope=\"col\">Machine name</th>"
                        + "<th scope=\"col\">Type</th><th scope=\"col\">Values</th>"
                        + "<th scope=\"col\">Actions</th></tr></thead><tbody>");

        for (FieldInstanceConfig instance : fields.instances(entityTypeId, bundle)) {
            FieldStorageConfig storage = fields.findStorage(entityTypeId, instance.fieldName()).orElseThrow();
            String path = fieldsPath(entityTypeId, bundle) + "/" + instance.fieldName();
            markup.append("<tr>")
                    .append(cell(instance.label()))
                    .append(cell(instance.fieldName()))
                    .append(cell(storage.type()))
                    .append(cell(storage.unlimited() ? "Unlimited" : String.valueOf(storage.cardinality())))
                    .append("<td>")
                    .append("<a class=\"btn btn-secondary btn-sm\" href=\"")
                    .append(HtmlUtils.htmlEscape(path)).append("\">Edit</a> ")
                    .append("<a class=\"btn btn-danger btn-sm\" href=\"")
                    .append(HtmlUtils.htmlEscape(path)).append("/delete\">Delete</a>")
                    .append("</td></tr>");
        }
        return markup.append("</tbody></table>").toString();
    }

    private static String cell(String text) {
        return "<td>" + HtmlUtils.htmlEscape(text) + "</td>";
    }

    /** Step one: what the field is called and what kind of value it holds. */
    private FormElement chooseTypeForm() {
        return FormElement.of(ElementType.CONTAINER, "add-field")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Label").markRequired())
                .child(FormElement.of(ElementType.TEXTFIELD, FIELD_NAME)
                        .label("Machine name")
                        .description("Lower case letters, numbers, and underscores.")
                        .markRequired()
                        .rule(ValidationRule.pattern("[a-z0-9_]+")))
                .child(FormElement.of(ElementType.SELECT, FIELD_TYPE)
                        .label("Field type")
                        .markRequired()
                        .options(fieldTypeOptions()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "continue").label("Continue")));
    }

    /** Step two: the settings the chosen type and its storage need. */
    private FormElement settingsForm(Map<String, String> chosen) {
        return FormElement.of(ElementType.CONTAINER, "field-settings")
                .child(FormElement.of(ElementType.HIDDEN, STEP).value("2"))
                .child(FormElement.of(ElementType.HIDDEN, LABEL).value(chosen.get(LABEL)))
                .child(FormElement.of(ElementType.HIDDEN, FIELD_NAME).value(chosen.get(FIELD_NAME)))
                .child(FormElement.of(ElementType.HIDDEN, FIELD_TYPE).value(chosen.get(FIELD_TYPE)))
                .child(cardinalityElement(1))
                .child(requiredElement(false))
                .child(descriptionElement(""))
                .child(defaultValueElement(""))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save field")));
    }

    private FormElement editForm(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return FormElement.of(ElementType.CONTAINER, "edit-field")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Label").markRequired().value(instance.label()))
                .child(cardinalityElement(storage.cardinality()))
                .child(requiredElement(instance.required()))
                .child(descriptionElement(instance.description()))
                .child(defaultValueElement(instance.defaultValue()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save settings")));
    }

    private static FormElement cardinalityElement(int value) {
        return FormElement.of(ElementType.NUMBER, CARDINALITY)
                .label("Values per entity")
                .description("How many values this field holds. Use -1 for as many as are given.")
                .value(value);
    }

    private static FormElement requiredElement(boolean checked) {
        return FormElement.of(ElementType.CHECKBOX, REQUIRED).label("Required field").value(checked);
    }

    private static FormElement descriptionElement(String value) {
        return FormElement.of(ElementType.TEXTFIELD, DESCRIPTION)
                .label("Help text")
                .description("Shown under the field on the edit form.")
                .value(value);
    }

    private static FormElement defaultValueElement(Object value) {
        return FormElement.of(ElementType.TEXTFIELD, DEFAULT_VALUE)
                .label("Default value")
                .value(value);
    }

    private List<SelectOption> fieldTypeOptions() {
        List<SelectOption> options = new ArrayList<>();
        pluginRegistry.managerFor(FieldType.class).ids().stream().sorted()
                .forEach(id -> options.add(new SelectOption(id, id)));
        return options;
    }

    private static int cardinality(Map<String, String> submitted) {
        String value = submitted.get(CARDINALITY);
        return (value == null || value.isBlank()) ? 1 : Integer.parseInt(value);
    }

    private static FieldInstanceConfig instance(
            String entityTypeId, String bundle, String fieldName, Map<String, String> submitted) {

        FieldInstanceConfig instance = FieldInstanceConfig
                .of(fieldName, entityTypeId, bundle, submitted.getOrDefault(LABEL, fieldName))
                .withDescription(submitted.getOrDefault(DESCRIPTION, ""))
                .withDefaultValue(submitted.get(DEFAULT_VALUE));
        return submitted.containsKey(REQUIRED) ? instance.asRequired() : instance;
    }
}
