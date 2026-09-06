package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.AllowedValues;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import org.springframework.web.util.HtmlUtils;

/**
 * The label of the option a list field holds, so a reader sees "Excellent"
 * rather than the 5 that was stored. A value no longer among the allowed ones is
 * shown as it stands.
 */
@SpringDropPlugin(id = ListDefaultFormatter.ID, type = FieldFormatter.class)
public class ListDefaultFormatter implements FieldFormatter {

    public static final String ID = "list_default";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        String label = AllowedValues.labelled(context.storage()).stream()
                .filter(allowed -> String.valueOf(allowed.value()).equals(String.valueOf(value)))
                .map(allowed -> allowed.label())
                .findFirst()
                .orElseGet(() -> String.valueOf(value));
        return HtmlUtils.htmlEscape(label);
    }
}
