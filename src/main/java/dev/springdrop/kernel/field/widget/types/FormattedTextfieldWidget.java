package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.file.EditorImages;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import tools.jackson.databind.ObjectMapper;

/** A single-line input for formatted text, with its format. */
@SpringDropPlugin(id = FormattedTextfieldWidget.ID, type = FieldWidget.class)
public class FormattedTextfieldWidget extends FormattedTextWidget {

    public static final String ID = "text_textfield";

    public FormattedTextfieldWidget(TextFormatManager formats, ObjectMapper objectMapper, EditorImages editorImages) {
        super(formats, objectMapper, editorImages);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    ElementType textInput() {
        return ElementType.TEXTFIELD;
    }
}
