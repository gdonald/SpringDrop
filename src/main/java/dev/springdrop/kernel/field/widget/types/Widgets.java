package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;

/**
 * The control every widget starts from: named for its delta, labelled and
 * described from the field instance, and required only on the first delta, since
 * a required field needs one value rather than every value it could hold.
 */
interface Widgets {

    /** The attribute a date input carries the first day of the site's week in, 0 for Sunday to 6 for Saturday. */
    String FIRST_DAY_ATTRIBUTE = "data-first-day";

    static FormElement firstDayOfWeek(FormElement element, DateFormatService dates) {
        return element.attribute(FIRST_DAY_ATTRIBUTE,
                String.valueOf(dates.settings().firstDayOfWeek().getValue() % 7));
    }

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
