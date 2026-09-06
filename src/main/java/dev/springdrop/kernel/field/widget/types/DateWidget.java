package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import java.util.Map;

/**
 * A native date input for a field storing dates, and a date and time input for
 * one storing moments. What the person enters is read in the site's timezone and
 * stored as an instant, so the same moment shows the same way it was typed.
 */
@SpringDropPlugin(id = DateWidget.ID, type = FieldWidget.class)
public class DateWidget implements FieldWidget {

    public static final String ID = "datetime_default";

    private final DateFormatService dateFormatService;

    public DateWidget(DateFormatService dateFormatService) {
        this.dateFormatService = dateFormatService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        if (dateOnly(context)) {
            return Widgets.control(ElementType.DATE, context, delta, value);
        }
        return Widgets.control(ElementType.DATETIME, context, delta,
                LocalDateTimes.toInput(value, dateFormatService.siteTimezone()));
    }

    @Override
    public Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        Object entered = submitted.get(context.elementName(delta));
        if (entered == null || entered.toString().isBlank()) {
            return null;
        }
        return dateOnly(context)
                ? entered
                : LocalDateTimes.toStored(entered.toString(), dateFormatService.siteTimezone());
    }

    static boolean dateOnly(WidgetContext context) {
        return DateTimeConstraint.DATE_ONLY.equals(DateTimeFieldType.datetimeType(context.storage()));
    }
}
