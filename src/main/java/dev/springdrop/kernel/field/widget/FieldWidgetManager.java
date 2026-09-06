package dev.springdrop.kernel.field.widget;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds the part of a form that edits one field, and reads it back. The widget
 * comes from the instance's {@code widget} setting, or from the field type's
 * default. Cardinality decides how many deltas are shown: one for a single-value
 * field, one per value for the rest, with an add-another button while the field
 * can hold more.
 */
@Component
public class FieldWidgetManager {

    public static final String WIDGET_SETTING = "widget";

    public static final String ADD_MORE = "add_more";

    /** The wrapper an add-another action replaces, named after the field. */
    public static final String CONTAINER_PREFIX = "field-";

    private final FieldConfigManager fieldConfigManager;
    private final PluginRegistry pluginRegistry;

    public FieldWidgetManager(FieldConfigManager fieldConfigManager, PluginRegistry pluginRegistry) {
        this.fieldConfigManager = fieldConfigManager;
        this.pluginRegistry = pluginRegistry;
    }

    public WidgetContext context(String entityTypeId, String bundle, String fieldName) {
        FieldStorageConfig storage = fieldConfigManager.findStorage(entityTypeId, fieldName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No field storage '" + fieldName + "' on entity type '" + entityTypeId + "'"));
        FieldInstanceConfig instance = fieldConfigManager.findInstance(entityTypeId, bundle, fieldName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Field '" + fieldName + "' is not on bundle '" + bundle + "'"));
        return new WidgetContext(storage, instance);
    }

    /** The widget editing this field: the one the instance names, else the type's default. */
    public FieldWidget widget(WidgetContext context) {
        Object named = context.instance().settings().get(WIDGET_SETTING);
        String widgetId = (named == null)
                ? fieldConfigManager.fieldType(context.storage().type()).defaultWidget()
                : named.toString();
        return pluginRegistry.managerFor(FieldWidget.class).get(widgetId);
    }

    /** The field as a form: one element per delta, plus an add-another button when more fit. */
    public FormElement build(WidgetContext context, List<Object> values, int extraDeltas) {
        FieldWidget widget = widget(context);
        int deltas = deltaCount(context, values.size() + extraDeltas);

        FormElement container = FormElement.of(ElementType.CONTAINER, CONTAINER_PREFIX + context.fieldName())
                .attribute("class", "mb-4")
                .attribute("id", CONTAINER_PREFIX + context.fieldName());
        for (int delta = 0; delta < deltas; delta++) {
            container.child(widget.element(context, delta, valueAt(values, delta)));
        }
        if (canHoldMore(context, deltas)) {
            container.child(addMoreButton(context));
            container.child(FormElement.of(ElementType.HIDDEN, "entity_type")
                    .value(context.instance().entityTypeId()));
            container.child(FormElement.of(ElementType.HIDDEN, "bundle").value(context.instance().bundle()));
            container.child(FormElement.of(ElementType.HIDDEN, "field").value(context.fieldName()));
        }
        return container;
    }

    /** The values a submission holds for this field, in delta order, empties left out. */
    public List<Object> extract(WidgetContext context, Map<String, Object> submitted) {
        FieldWidget widget = widget(context);
        List<Object> values = new ArrayList<>();
        for (int delta = 0; delta < submittedDeltaLimit(context, submitted); delta++) {
            Object value = widget.extract(context, delta, submitted);
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static FormElement addMoreButton(WidgetContext context) {
        String target = "#" + CONTAINER_PREFIX + context.fieldName();
        return FormElement.of(ElementType.SUBMIT, ADD_MORE + "_" + context.fieldName())
                .label("Add another item")
                .attribute("class", "btn btn-secondary btn-sm")
                .attribute("hx-post", FieldWidgetPaths.ADD_MORE)
                .attribute("hx-target", target)
                .attribute("hx-swap", "outerHTML")
                .attribute("hx-include", "closest form");
    }

    /** At least one delta, never more than the field can hold. */
    private static int deltaCount(WidgetContext context, int wanted) {
        int deltas = Math.max(1, wanted);
        return context.unlimited() ? deltas : Math.min(deltas, context.cardinality());
    }

    private static boolean canHoldMore(WidgetContext context, int deltas) {
        return context.unlimited() || deltas < context.cardinality();
    }

    /** How far to read: everything submitted for an unlimited field, else its cardinality. */
    private static int submittedDeltaLimit(WidgetContext context, Map<String, Object> submitted) {
        return context.unlimited() ? submitted.size() : context.cardinality();
    }

    private static Object valueAt(List<Object> values, int delta) {
        return (delta < values.size()) ? values.get(delta) : null;
    }
}
