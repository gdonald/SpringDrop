package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;

/** Uploads files, asking for a description of each when the field is set to. */
@SpringDropPlugin(id = FileGenericWidget.ID, type = FieldWidget.class)
public class FileGenericWidget extends FileWidget {

    public static final String ID = "file_generic";

    public FileGenericWidget(FileService files,
            @Value("${spring.servlet.multipart.max-file-size:32MB}") DataSize siteMaxFilesize) {
        super(files, siteMaxFilesize);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected String uploadLabel() {
        return "Add a file";
    }

    @Override
    protected List<FormElement> valueInputs(WidgetContext context, int delta, Object value) {
        if (!Boolean.TRUE.equals(context.instance().settings().get(FileItem.DESCRIPTION_FIELD))) {
            return List.of();
        }
        return List.of(FormElement.of(ElementType.TEXTFIELD, partName(context, delta, FileItem.DESCRIPTION))
                .label("Description")
                .value(FileItem.part(value, FileItem.DESCRIPTION))
                .rule(ValidationRule.maxLength(FileItem.MAX_DESCRIPTION_LENGTH)));
    }

    @Override
    protected Map<String, Object> valueOf(WidgetContext context, int delta, ManagedFile file,
            Map<String, Object> submitted) {
        Map<String, Object> value = target(file);
        value.put(FileItem.DISPLAY, true);
        if (Boolean.TRUE.equals(context.instance().settings().get(FileItem.DESCRIPTION_FIELD))) {
            value.put(FileItem.DESCRIPTION, entered(context, delta, FileItem.DESCRIPTION, submitted));
        }
        return value;
    }
}
