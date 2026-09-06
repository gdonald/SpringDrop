package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import org.springframework.web.util.HtmlUtils;

/**
 * Yes or no, or whichever pair of words the display prefers, so a published flag
 * can read "Published" and "Draft" rather than true and false.
 */
@SpringDropPlugin(id = BooleanFormatter.ID, type = FieldFormatter.class)
public class BooleanFormatter implements FieldFormatter {

    public static final String ID = "boolean";

    public static final String TRUE_LABEL = "true_label";

    public static final String FALSE_LABEL = "false_label";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        boolean on = Boolean.TRUE.equals(value) || "true".equals(String.valueOf(value));
        return HtmlUtils.htmlEscape(on
                ? context.text(TRUE_LABEL, "Yes")
                : context.text(FALSE_LABEL, "No"));
    }
}
