package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import org.springframework.web.util.HtmlUtils;

/**
 * The value as plain text, escaped so nothing stored can alter the page.
 */
@SpringDropPlugin(id = StringFormatter.ID, type = FieldFormatter.class)
public class StringFormatter implements FieldFormatter {

    public static final String ID = "string";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return HtmlUtils.htmlEscape(String.valueOf(value));
    }
}
