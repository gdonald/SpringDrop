package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;

/**
 * A single checkbox, labelled with what being on means. A box left unticked is
 * not submitted at all, so the widget reads a missing value as off rather than
 * as nothing entered.
 */
@SpringDropPlugin(id = BooleanCheckboxWidget.ID, type = FieldWidget.class)
public class BooleanCheckboxWidget implements FieldWidget {

    public static final String ID = "boolean_checkbox";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        FormElement element = Widgets.control(ElementType.CHECKBOX, context, delta, isOn(value));
        Object onLabel = context.instance().settings().get(FieldSettings.ON_LABEL);
        return (onLabel == null) ? element : element.label(onLabel.toString());
    }

    @Override
    public Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        return isOn(submitted.get(context.elementName(delta)));
    }

    private static boolean isOn(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value)) || "on".equals(value);
    }
}
