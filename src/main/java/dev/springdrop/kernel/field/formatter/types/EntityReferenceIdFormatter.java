package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import org.springframework.web.util.HtmlUtils;

/**
 * The bare id a reference holds, for a display that feeds something else rather
 * than a reader.
 */
@SpringDropPlugin(id = EntityReferenceIdFormatter.ID, type = FieldFormatter.class)
public class EntityReferenceIdFormatter implements FieldFormatter {

    public static final String ID = "entity_reference_entity_id";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return HtmlUtils.htmlEscape(String.valueOf(value));
    }
}
