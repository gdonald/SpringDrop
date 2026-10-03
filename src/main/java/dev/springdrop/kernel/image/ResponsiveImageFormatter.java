package dev.springdrop.kernel.image;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * An image as a {@code picture} element drawn with a responsive image style,
 * so each breakpoint gets an image of a fitting size. Without a responsive
 * image style the site has, the original is drawn as a plain {@code img}.
 */
@SpringDropPlugin(id = ResponsiveImageFormatter.ID, type = FieldFormatter.class)
public class ResponsiveImageFormatter implements FieldFormatter {

    public static final String ID = "responsive_image";

    public static final String RESPONSIVE_IMAGE_STYLE = "responsive_image_style";

    private final FileService files;
    private final ResponsiveImageStyleManager responsiveStyles;
    private final ImageMarkup markup;

    public ResponsiveImageFormatter(FileService files, ResponsiveImageStyleManager responsiveStyles,
            ImageMarkup markup) {
        this.files = files;
        this.responsiveStyles = responsiveStyles;
        this.markup = markup;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        return FileItem.fileId(value).flatMap(files::find).map(file -> {
            String attributes = ImageMarkup.attributes(value, context.settings());
            String image = responsiveStyles.find(String.valueOf(context.settings().get(RESPONSIVE_IMAGE_STYLE)))
                    .map(style -> responsiveStyles.render(style, file, ImageMarkup.storedSize(value).orElse(null),
                            attributes))
                    .orElseGet(() -> "<img src=\"" + HtmlUtils.htmlEscape(files.url(file)) + "\"" + attributes + ">");
            return markup.linked(image, context, file);
        }).orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        List<SelectOption> choices = new ArrayList<>();
        responsiveStyles.all().forEach(style -> choices.add(new SelectOption(style.id(), style.label())));
        List<FormElement> elements = new ArrayList<>(List.of(
                FormElement.of(ElementType.SELECT, prefix + RESPONSIVE_IMAGE_STYLE)
                        .label("Responsive image style")
                        .value(String.valueOf(settings.getOrDefault(RESPONSIVE_IMAGE_STYLE, "")))
                        .options(choices)));
        elements.addAll(ImageMarkup.linkAndLoadingForm(prefix, settings));
        return elements;
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> settings = new LinkedHashMap<>();
        String chosen = submitted.getOrDefault(prefix + RESPONSIVE_IMAGE_STYLE, "");
        settings.put(RESPONSIVE_IMAGE_STYLE, responsiveStyles.find(chosen).isPresent() ? chosen : "");
        ImageMarkup.putLinkAndLoading(prefix, submitted, settings);
        return settings;
    }
}
