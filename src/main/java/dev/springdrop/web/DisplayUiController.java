package dev.springdrop.web;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Where a bundle's fields sit when it is edited, and where they sit when it is
 * read. Both tabs are the same shape: one row per field with its handler, its
 * place in the order, and whether it is shown at all, and the reading tab adds
 * whether the label is shown. Each view mode is laid out on its own.
 */
@Controller
public class DisplayUiController {

    public static final String WIDGET_SUFFIX = "_widget";

    public static final String FORMATTER_SUFFIX = "_formatter";

    public static final String WEIGHT_SUFFIX = "_weight";

    public static final String SHOWN_SUFFIX = "_shown";

    public static final String LABEL_SUFFIX = "_label";

    public static final String MODE = "mode";

    private final FieldConfigManager fields;
    private final FormDisplayManager formDisplays;
    private final ViewDisplayManager viewDisplays;
    private final FormRenderer renderer;
    private final PluginRegistry pluginRegistry;

    public DisplayUiController(
            FieldConfigManager fields,
            FormDisplayManager formDisplays,
            ViewDisplayManager viewDisplays,
            FormRenderer renderer,
            PluginRegistry pluginRegistry) {
        this.fields = fields;
        this.formDisplays = formDisplays;
        this.viewDisplays = viewDisplays;
        this.renderer = renderer;
        this.pluginRegistry = pluginRegistry;
    }

    public static String formDisplayPath(String entityTypeId, String bundle) {
        return FieldUiController.PATH_PREFIX + "/" + entityTypeId + "/" + bundle + "/form-display";
    }

    public static String viewDisplayPath(String entityTypeId, String bundle) {
        return FieldUiController.PATH_PREFIX + "/" + entityTypeId + "/" + bundle + "/display";
    }

    @GetMapping(FieldUiController.PATH_PREFIX + "/{entityType}/{bundle}/form-display")
    public String formDisplay(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @RequestParam(name = MODE, defaultValue = FormDisplayConfig.DEFAULT_MODE) String mode,
            Model model) {

        FormDisplayConfig display = formDisplays.find(entityType, bundle, mode)
                .orElseGet(() -> FormDisplayConfig.of(entityType, bundle, mode));

        model.addAttribute("title", "Manage form display");
        model.addAttribute("description", "Where each field sits on the edit form, in the "
                + mode + " form mode.");
        model.addAttribute("action", formDisplayPath(entityType, bundle) + "?" + MODE + "=" + mode);
        model.addAttribute("formMarkup", renderer.render(displayForm(
                entityType, bundle, mode, WIDGET_SUFFIX, widgetOptions(),
                display.slots(), Set.copyOf(display.disabled()), null)));
        return "admin/field-ui";
    }

    @PostMapping(FieldUiController.PATH_PREFIX + "/{entityType}/{bundle}/form-display")
    public String saveFormDisplay(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @RequestParam(name = MODE, defaultValue = FormDisplayConfig.DEFAULT_MODE) String mode,
            @RequestParam Map<String, String> submitted) {

        FormDisplayConfig display = FormDisplayConfig.of(entityType, bundle, mode);
        for (FieldInstanceConfig instance : fields.instances(entityType, bundle)) {
            String field = instance.fieldName();
            display = submitted.containsKey(field + SHOWN_SUFFIX)
                    ? display.with(slot(field, submitted, WIDGET_SUFFIX))
                    : display.withoutField(field);
        }
        formDisplays.save(display);
        return "redirect:" + formDisplayPath(entityType, bundle);
    }

    @GetMapping(FieldUiController.PATH_PREFIX + "/{entityType}/{bundle}/display")
    public String viewDisplay(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @RequestParam(name = MODE, defaultValue = ViewDisplayConfig.DEFAULT_MODE) String mode,
            Model model) {

        ViewDisplayConfig display = viewDisplays.find(entityType, bundle, mode)
                .orElseGet(() -> ViewDisplayConfig.of(entityType, bundle, mode));

        model.addAttribute("title", "Manage display");
        model.addAttribute("description", "How each field reads, in the " + mode + " view mode.");
        model.addAttribute("action", viewDisplayPath(entityType, bundle) + "?" + MODE + "=" + mode);
        model.addAttribute("formMarkup", renderer.render(displayForm(
                entityType, bundle, mode, FORMATTER_SUFFIX, formatterOptions(),
                display.slots(), Set.copyOf(display.disabled()), Set.copyOf(display.hiddenLabels()))));
        return "admin/field-ui";
    }

    @PostMapping(FieldUiController.PATH_PREFIX + "/{entityType}/{bundle}/display")
    public String saveViewDisplay(
            @PathVariable String entityType,
            @PathVariable String bundle,
            @RequestParam(name = MODE, defaultValue = ViewDisplayConfig.DEFAULT_MODE) String mode,
            @RequestParam Map<String, String> submitted) {

        ViewDisplayConfig display = ViewDisplayConfig.of(entityType, bundle, mode);
        for (FieldInstanceConfig instance : fields.instances(entityType, bundle)) {
            String field = instance.fieldName();
            display = submitted.containsKey(field + SHOWN_SUFFIX)
                    ? display.with(slot(field, submitted, FORMATTER_SUFFIX))
                    : display.withoutField(field);
            if (!submitted.containsKey(field + LABEL_SUFFIX)) {
                display = display.withoutLabel(field);
            }
        }
        viewDisplays.save(display);
        return "redirect:" + viewDisplayPath(entityType, bundle);
    }

    /** One row per field: its handler, its weight, whether it is shown, and its label. */
    private FormElement displayForm(
            String entityTypeId,
            String bundle,
            String mode,
            String handlerSuffix,
            List<SelectOption> handlers,
            Map<String, FieldDisplaySlot> slots,
            Set<String> disabled,
            Set<String> hiddenLabels) {

        FormElement form = FormElement.of(ElementType.CONTAINER, "display")
                .child(FormElement.of(ElementType.HIDDEN, MODE).value(mode));

        for (FieldInstanceConfig instance : fields.instances(entityTypeId, bundle)) {
            String field = instance.fieldName();
            FieldDisplaySlot slot = slots.get(field);

            FormElement row = FormElement.of(ElementType.FIELDSET, field).label(instance.label())
                    .child(FormElement.of(ElementType.CHECKBOX, field + SHOWN_SUFFIX)
                            .label("Shown")
                            .value(!disabled.contains(field)))
                    .child(FormElement.of(ElementType.SELECT, field + handlerSuffix)
                            .label("Handler")
                            .options(handlers)
                            .value(handlerOf(entityTypeId, field, slot, handlerSuffix)))
                    .child(FormElement.of(ElementType.NUMBER, field + WEIGHT_SUFFIX)
                            .label("Weight")
                            .value(slot == null ? 0 : slot.weight()));
            if (hiddenLabels != null) {
                row.child(FormElement.of(ElementType.CHECKBOX, field + LABEL_SUFFIX)
                        .label("Show label")
                        .value(!hiddenLabels.contains(field)));
            }
            form.child(row);
        }

        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save layout")));
    }

    /** The handler already chosen, or the one the field type gets by default. */
    private String handlerOf(
            String entityTypeId, String field, FieldDisplaySlot slot, String handlerSuffix) {

        if (slot != null) {
            return slot.handler();
        }
        FieldStorageConfig storage = fields.findStorage(entityTypeId, field).orElseThrow();
        dev.springdrop.kernel.field.FieldType type = fields.fieldType(storage.type());
        return WIDGET_SUFFIX.equals(handlerSuffix) ? type.defaultWidget() : type.defaultFormatter();
    }

    private static FieldDisplaySlot slot(String field, Map<String, String> submitted, String handlerSuffix) {
        String handler = submitted.get(field + handlerSuffix);
        String weight = submitted.get(field + WEIGHT_SUFFIX);
        return FieldDisplaySlot.of(field, handler, (weight == null || weight.isBlank())
                ? 0 : Integer.parseInt(weight));
    }

    private List<SelectOption> widgetOptions() {
        return options(pluginRegistry.managerFor(FieldWidget.class).ids());
    }

    private List<SelectOption> formatterOptions() {
        return options(pluginRegistry.managerFor(FieldFormatter.class).ids());
    }

    private static List<SelectOption> options(Set<String> ids) {
        List<SelectOption> options = new ArrayList<>();
        ids.stream().sorted().forEach(id -> options.add(new SelectOption(id, id)));
        return options;
    }
}
