package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/** The text through its format's filters and the sanitizer. */
@SpringDropPlugin(id = FormattedTextFormatter.ID, type = FieldFormatter.class)
public class FormattedTextFormatter implements FieldFormatter {

    public static final String ID = "text_default";

    private final TextFormatManager formats;

    public FormattedTextFormatter(TextFormatManager formats) {
        this.formats = formats;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return formats.process(FormattedText.part(value, FormattedText.VALUE),
                FormattedText.part(value, FormattedText.FORMAT));
    }
}
