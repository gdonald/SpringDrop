package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A pair of inputs for the two ends of a range, grouped so an error about the
 * range lands on the pair rather than on one end of it. Each end is read and
 * shown the way a single date field is.
 */
@SpringDropPlugin(id = DateRangeWidget.ID, type = FieldWidget.class)
public class DateRangeWidget implements FieldWidget {

    public static final String ID = "daterange_default";

    private final DateFormatService dateFormatService;

    public DateRangeWidget(DateFormatService dateFormatService) {
        this.dateFormatService = dateFormatService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        ZoneId zone = dateFormatService.siteTimezone();
        boolean dateOnly = DateWidget.dateOnly(context);
        Map<?, ?> range = (value instanceof Map<?, ?> stored) ? stored : Map.of();

        return FormElement.of(ElementType.FIELDSET, context.elementName(delta))
                .label(delta == 0 ? context.label() : "")
                .child(end(context, delta, DateRangeConstraint.START, "Start",
                        range.get(DateRangeConstraint.START), dateOnly, zone))
                .child(end(context, delta, DateRangeConstraint.END, "End",
                        range.get(DateRangeConstraint.END), dateOnly, zone));
    }

    @Override
    public Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        String start = entered(context, delta, DateRangeConstraint.START, submitted);
        String end = entered(context, delta, DateRangeConstraint.END, submitted);
        if (start.isBlank() && end.isBlank()) {
            return null;
        }

        Map<String, Object> range = new LinkedHashMap<>();
        range.put(DateRangeConstraint.START, stored(context, start));
        range.put(DateRangeConstraint.END, stored(context, end));
        return range;
    }

    private FormElement end(
            WidgetContext context,
            int delta,
            String endName,
            String label,
            Object value,
            boolean dateOnly,
            ZoneId zone) {

        ElementType type = dateOnly ? ElementType.DATE : ElementType.DATETIME;
        Object shown = dateOnly ? value : LocalDateTimes.toInput(value, zone);
        return FormElement.of(type, elementName(context, delta, endName)).label(label).value(shown);
    }

    private String stored(WidgetContext context, String entered) {
        if (entered.isBlank() || DateWidget.dateOnly(context)) {
            return entered;
        }
        return LocalDateTimes.toStored(entered, dateFormatService.siteTimezone());
    }

    private static String entered(
            WidgetContext context, int delta, String endName, Map<String, Object> submitted) {

        Object value = submitted.get(elementName(context, delta, endName));
        return (value == null) ? "" : value.toString();
    }

    private static String elementName(WidgetContext context, int delta, String endName) {
        return context.elementName(delta) + ":" + endName;
    }
}
