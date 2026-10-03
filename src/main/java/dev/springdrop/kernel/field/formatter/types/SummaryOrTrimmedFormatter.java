package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/** The summary through the text's format when there is one, and otherwise the trimmed text. */
@SpringDropPlugin(id = SummaryOrTrimmedFormatter.ID, type = FieldFormatter.class)
public class SummaryOrTrimmedFormatter implements FieldFormatter {

    public static final String ID = "text_summary_or_trimmed";

    private final TextFormatManager formats;

    public SummaryOrTrimmedFormatter(TextFormatManager formats) {
        this.formats = formats;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        String summary = FormattedText.part(value, FormattedText.SUMMARY);
        return summary.isBlank()
                ? TrimmedTextFormatter.trimmed(formats, context, value)
                : formats.process(summary, FormattedText.part(value, FormattedText.FORMAT));
    }
}
