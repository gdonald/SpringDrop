package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;

/**
 * A number input carrying the bounds and step the field settings give it. The
 * browser enforces them through the native input and through the rule the server
 * checks, so both sides agree.
 */
@SpringDropPlugin(id = NumberWidget.ID, type = FieldWidget.class)
public class NumberWidget implements FieldWidget {

    public static final String ID = "number";

    public static final String STEP = "step";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        FormElement element = Widgets.control(ElementType.NUMBER, context, delta, value);
        Map<String, Object> settings = context.instance().settings();

        String min = text(settings.get(FieldSettings.MIN));
        String max = text(settings.get(FieldSettings.MAX));
        if (!min.isEmpty()) {
            element.attribute("min", min);
        }
        if (!max.isEmpty()) {
            element.attribute("max", max);
        }
        if (settings.containsKey(STEP)) {
            element.attribute(STEP, text(settings.get(STEP)));
        }
        if (!min.isEmpty() || !max.isEmpty()) {
            element.rule(ValidationRule.range(min, max));
        }
        return element;
    }

    private static String text(Object setting) {
        return (setting == null) ? "" : setting.toString();
    }
}
