package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import org.springframework.web.util.HtmlUtils;

/**
 * Text with its line breaks kept, for values typed into a textarea.
 */
@SpringDropPlugin(id = BasicStringFormatter.ID, type = FieldFormatter.class)
public class BasicStringFormatter implements FieldFormatter {

    public static final String ID = "basic_string";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return HtmlUtils.htmlEscape(String.valueOf(value)).replace("\n", "<br>");
    }
}
