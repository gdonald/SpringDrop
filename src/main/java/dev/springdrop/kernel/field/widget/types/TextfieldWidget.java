package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/**
 * A single-line text input, the widget a string field gets by default.
 */
@SpringDropPlugin(id = TextfieldWidget.ID, type = FieldWidget.class)
public class TextfieldWidget implements FieldWidget {

    public static final String ID = "string_textfield";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        return Widgets.control(ElementType.TEXTFIELD, context, delta, value);
    }
}
