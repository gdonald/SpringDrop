package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;

/**
 * The control every widget starts from: named for its delta, labelled and
 * described from the field instance, and required only on the first delta, since
 * a required field needs one value rather than every value it could hold.
 */
interface Widgets {

    static FormElement control(ElementType type, WidgetContext context, int delta, Object value) {
        FormElement element = FormElement.of(type, context.elementName(delta)).value(value);
        if (delta == 0) {
            element.label(context.label()).description(context.instance().description());
            if (context.required()) {
                element.markRequired();
            }
        }
        return element;
    }
}
