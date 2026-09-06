package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/**
 * A multi-line text input, for text with no length limit.
 */
@SpringDropPlugin(id = TextareaWidget.ID, type = FieldWidget.class)
public class TextareaWidget implements FieldWidget {

    public static final String ID = "string_textarea";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        return Widgets.control(ElementType.TEXTAREA, context, delta, value);
    }
}
