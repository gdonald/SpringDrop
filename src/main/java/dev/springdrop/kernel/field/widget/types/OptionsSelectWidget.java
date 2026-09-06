package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.AllowedValues;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayList;
import java.util.List;

/**
 * A drop-down of the field's allowed values. A field that is not required also
 * offers an empty choice, so a person can leave it unanswered.
 */
@SpringDropPlugin(id = OptionsSelectWidget.ID, type = FieldWidget.class)
public class OptionsSelectWidget implements FieldWidget {

    public static final String ID = "options_select";

    public static final String EMPTY_LABEL = "- None -";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        return Widgets.control(ElementType.SELECT, context, delta, value)
                .options(options(context));
    }

    static List<SelectOption> options(WidgetContext context) {
        List<SelectOption> options = new ArrayList<>();
        if (!context.required()) {
            options.add(new SelectOption("", EMPTY_LABEL));
        }
        AllowedValues.labelled(context.storage())
                .forEach(allowed -> options.add(
                        new SelectOption(String.valueOf(allowed.value()), allowed.label())));
        return options;
    }
}
