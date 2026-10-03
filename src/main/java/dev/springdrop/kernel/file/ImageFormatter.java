package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.image.ImageMarkup;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/**
 * An image as an {@code img} element, drawn with an image style or as the
 * original, with its alternative text, title, and the size it is drawn at. It
 * can link to the content it belongs to or to the original file, and loads
 * lazily unless set to load with the page. A value pointing at a file that is
 * gone shows nothing.
 */
@SpringDropPlugin(id = ImageFormatter.ID, type = FieldFormatter.class)
public class ImageFormatter implements FieldFormatter {

    public static final String ID = "image";

    /** The image style the image is drawn with, blank for the original. */
    public static final String IMAGE_STYLE = "image_style";

    private final FileService files;
    private final ImageStyleManager styles;
    private final ImageMarkup markup;

    public ImageFormatter(FileService files, ImageStyleManager styles, ImageMarkup markup) {
        this.files = files;
        this.styles = styles;
        this.markup = markup;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return FileItem.fileId(value).flatMap(files::find).map(file -> {
            Optional<ImageStyle> style = Optional.ofNullable(context.settings().get(IMAGE_STYLE))
                    .flatMap(id -> styles.find(id.toString()));
            Optional<ImageSize> drawn = ImageMarkup.storedSize(value)
                    .flatMap(size -> style.map(found -> styles.transformedSize(found, size)).orElse(Optional.of(size)));
            StringBuilder image = new StringBuilder("<img class=\"img-fluid\" src=\"")
                    .append(HtmlUtils.htmlEscape(style.map(found -> styles.url(found.id(), file))
                            .orElseGet(() -> files.url(file))))
                    .append("\"");
            drawn.ifPresent(size -> image.append(" width=\"").append(size.width())
                    .append("\" height=\"").append(size.height()).append("\""));
            image.append(ImageMarkup.attributes(value, context.settings())).append(">");
            return markup.linked(image.toString(), context, file);
        }).orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        List<SelectOption> choices = new ArrayList<>(List.of(new SelectOption("", "None (original image)")));
        styles.all().forEach(style -> choices.add(new SelectOption(style.id(), style.label())));
        List<FormElement> elements = new ArrayList<>(List.of(FormElement.of(ElementType.SELECT, prefix + IMAGE_STYLE)
                .label("Image style")
                .value(String.valueOf(settings.getOrDefault(IMAGE_STYLE, "")))
                .options(choices)));
        elements.addAll(ImageMarkup.linkAndLoadingForm(prefix, settings));
        return elements;
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> settings = new LinkedHashMap<>();
        String chosen = submitted.getOrDefault(prefix + IMAGE_STYLE, "");
        settings.put(IMAGE_STYLE, styles.find(chosen).isPresent() ? chosen : "");
        ImageMarkup.putLinkAndLoading(prefix, submitted, settings);
        return settings;
    }
}
