package dev.springdrop.kernel.field.widget;

import dev.springdrop.kernel.form.FormElement;
import java.util.Map;

/**
 * Turns a field's values into form elements, and submitted values back into
 * field values. A widget is a plugin registered with
 * {@code @SpringDropPlugin(type = FieldWidget.class)}, so a module adds one
 * without core knowing about it. Cardinality is handled around the widget: it is
 * asked for one element per delta.
 */
public interface FieldWidget {

    String id();

    /** The element editing one value of the field. */
    FormElement element(WidgetContext context, int delta, Object value);

    /** The value the person entered at one delta, or null when they left it empty. */
    default Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        Object value = submitted.get(context.elementName(delta));
        return (value == null || value.toString().isBlank()) ? null : value;
    }
}
