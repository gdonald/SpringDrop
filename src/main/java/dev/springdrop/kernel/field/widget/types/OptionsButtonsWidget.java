package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/**
 * The field's allowed values as radio buttons, for a short list where seeing
 * every choice at once helps. A field holding several values shows checkboxes
 * instead, since more than one can be picked.
 */
@SpringDropPlugin(id = OptionsButtonsWidget.ID, type = FieldWidget.class)
public class OptionsButtonsWidget implements FieldWidget {

    public static final String ID = "options_buttons";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        ElementType type = context.single() ? ElementType.RADIOS : ElementType.CHECKBOXES;
        return Widgets.control(type, context, delta, value)
                .options(OptionsSelectWidget.options(context));
    }
}
