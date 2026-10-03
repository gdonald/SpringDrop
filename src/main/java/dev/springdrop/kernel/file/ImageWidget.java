package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;

/**
 * Uploads images, showing a preview of each and asking for its alternative
 * text and title as the field is set to. The width and height are read from
 * the image on save rather than taken from the form.
 */
@SpringDropPlugin(id = ImageWidget.ID, type = FieldWidget.class)
public class ImageWidget extends FileWidget {

    public static final String ID = "image_image";

    public ImageWidget(FileService files,
            @Value("${spring.servlet.multipart.max-file-size:32MB}") DataSize siteMaxFilesize) {
        super(files, siteMaxFilesize);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected String uploadLabel() {
        return "Add an image";
    }

    @Override
    protected List<FormElement> valueInputs(WidgetContext context, int delta, Object value) {
        Map<String, Object> settings = context.instance().settings();
        List<FormElement> inputs = new ArrayList<>();
        if (Boolean.TRUE.equals(settings.get(FileItem.ALT_FIELD))) {
            FormElement alt = FormElement.of(ElementType.TEXTFIELD, partName(context, delta, FileItem.ALT))
                    .label("Alternative text")
                    .description("A short description of the image for people who cannot see it.")
                    .value(FileItem.part(value, FileItem.ALT))
                    .rule(ValidationRule.maxLength(FileItem.MAX_IMAGE_TEXT_LENGTH));
            if (Boolean.TRUE.equals(settings.get(FileItem.ALT_FIELD_REQUIRED))) {
                alt.markRequired();
            }
            inputs.add(alt);
        }
        if (Boolean.TRUE.equals(settings.get(FileItem.TITLE_FIELD))) {
            inputs.add(FormElement.of(ElementType.TEXTFIELD, partName(context, delta, FileItem.TITLE))
                    .label("Title")
                    .value(FileItem.part(value, FileItem.TITLE))
                    .rule(ValidationRule.maxLength(FileItem.MAX_IMAGE_TEXT_LENGTH)));
        }
        return inputs;
    }

    @Override
    protected Map<String, Object> valueOf(WidgetContext context, int delta, ManagedFile file,
            Map<String, Object> submitted) {
        Map<String, Object> value = target(file);
        value.put(FileItem.ALT, entered(context, delta, FileItem.ALT, submitted));
        value.put(FileItem.TITLE, entered(context, delta, FileItem.TITLE, submitted));
        files().imageSize(file).ifPresent(size -> {
            value.put(FileItem.WIDTH, size.width());
            value.put(FileItem.HEIGHT, size.height());
        });
        return value;
    }
}
