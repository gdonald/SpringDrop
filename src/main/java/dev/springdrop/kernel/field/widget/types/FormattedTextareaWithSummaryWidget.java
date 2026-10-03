package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.file.EditorImages;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import tools.jackson.databind.ObjectMapper;

/** A multi-line input for formatted text, with its format, and a summary input above it unless the field hides it. */
@SpringDropPlugin(id = FormattedTextareaWithSummaryWidget.ID, type = FieldWidget.class)
public class FormattedTextareaWithSummaryWidget extends FormattedTextWidget {

    public static final String ID = "text_textarea_with_summary";

    public FormattedTextareaWithSummaryWidget(TextFormatManager formats, ObjectMapper objectMapper, EditorImages editorImages) {
        super(formats, objectMapper, editorImages);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    ElementType textInput() {
        return ElementType.TEXTAREA;
    }

    @Override
    boolean withSummary(WidgetContext context) {
        return !Boolean.FALSE.equals(context.instance().settings().get(FormattedText.DISPLAY_SUMMARY));
    }
}
