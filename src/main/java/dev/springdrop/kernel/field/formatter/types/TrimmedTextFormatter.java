package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.filter.HtmlTrimmer;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/**
 * The start of the text, through its format, cut to the {@code trim_length}
 * setting at the last whole word that fits, 600 characters by default.
 */
@SpringDropPlugin(id = TrimmedTextFormatter.ID, type = FieldFormatter.class)
public class TrimmedTextFormatter implements FieldFormatter {

    public static final String ID = "text_trimmed";

    public static final String TRIM_LENGTH = "trim_length";

    public static final int DEFAULT_TRIM_LENGTH = 600;

    private final TextFormatManager formats;

    public TrimmedTextFormatter(TextFormatManager formats) {
        this.formats = formats;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return trimmed(formats, context, value);
    }

    static String trimmed(TextFormatManager formats, FormatterContext context, Object value) {
        String processed = formats.process(FormattedText.part(value, FormattedText.VALUE),
                FormattedText.part(value, FormattedText.FORMAT));
        return HtmlTrimmer.trim(processed, Integer.parseInt(context.text(TRIM_LENGTH, String.valueOf(DEFAULT_TRIM_LENGTH))));
    }
}
