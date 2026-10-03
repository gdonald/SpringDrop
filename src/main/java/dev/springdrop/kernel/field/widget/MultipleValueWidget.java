package dev.springdrop.kernel.field.widget;

import dev.springdrop.kernel.form.FormElement;
import java.util.List;
import java.util.Map;

/**
 * A widget that edits every value of a field in one control, such as a tags
 * input holding them all separated by commas, rather than one control per
 * delta. The field shows no add-another button.
 */
public interface MultipleValueWidget extends FieldWidget {

    /** The one element editing all of the field's values. */
    FormElement elementForAll(WidgetContext context, List<Object> values);

    /** Every value the person entered, in the order they entered them. */
    List<Object> extractAll(WidgetContext context, Map<String, Object> submitted);

    @Override
    default FormElement element(WidgetContext context, int delta, Object value) {
        return elementForAll(context, (value == null) ? List.of() : List.of(value));
    }
}
