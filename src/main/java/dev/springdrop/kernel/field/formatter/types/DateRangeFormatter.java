package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * Both ends of a range, each shown the way a single moment is, joined by the
 * word the display prefers.
 */
@SpringDropPlugin(id = DateRangeFormatter.ID, type = FieldFormatter.class)
public class DateRangeFormatter implements FieldFormatter {

    public static final String ID = "daterange_default";

    public static final String SEPARATOR = "separator";

    public static final String DEFAULT_SEPARATOR = " to ";

    private final DateTimeFormatter momentFormatter;

    public DateRangeFormatter(DateTimeFormatter momentFormatter) {
        this.momentFormatter = momentFormatter;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        if (!(value instanceof Map<?, ?> range)) {
            return HtmlUtils.htmlEscape(String.valueOf(value));
        }

        String start = momentFormatter.rendered(context, range.get(DateRangeConstraint.START));
        String end = momentFormatter.rendered(context, range.get(DateRangeConstraint.END));
        return HtmlUtils.htmlEscape(start + context.text(SEPARATOR, DEFAULT_SEPARATOR) + end);
    }
}
