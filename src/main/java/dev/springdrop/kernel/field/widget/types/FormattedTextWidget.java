package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.FieldWidgetPaths;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.EditorImages;
import dev.springdrop.kernel.file.UploadLimits;
import dev.springdrop.kernel.filter.TextFormat;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;

/**
 * Formatted text: the text, written in a format chosen from the formats the
 * person may use, and for text with a summary, the summary. The parts are
 * grouped so an error about the value lands on the group. The text input names
 * the tags each offered format keeps, for an editor to offer those and no more.
 */
abstract class FormattedTextWidget implements FieldWidget {

    /** Marks the input an editor attaches to. */
    public static final String EDITOR_TARGET = "data-editor-target";

    /** The tags each offered format keeps, as JSON keyed by format id. */
    public static final String EDITOR_FORMATS = "data-editor-formats";

    /** Where an editor sends an image to be uploaded. */
    public static final String EDITOR_UPLOAD = "data-editor-upload";

    /** Marks the format choice an editor follows. */
    public static final String FORMAT_SELECTOR = "data-editor-format-selector";

    /** Prefixes the attributes carrying what an editor image is held to and the messages refusing one. */
    public static final String EDITOR_IMAGE_PREFIX = "data-editor-image-";

    private final TextFormatManager formats;
    private final ObjectMapper objectMapper;
    private final EditorImages editorImages;

    FormattedTextWidget(TextFormatManager formats, ObjectMapper objectMapper, EditorImages editorImages) {
        this.formats = formats;
        this.objectMapper = objectMapper;
        this.editorImages = editorImages;
    }

    /** The input the text is written in. */
    abstract ElementType textInput();

    /** Whether the widget asks for a summary as well. */
    boolean withSummary(WidgetContext context) {
        return false;
    }

    public static String partName(WidgetContext context, int delta, String part) {
        return context.elementName(delta) + ":" + part;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        List<TextFormat> usable = formats.formatsFor(SecurityContextHolder.getContext().getAuthentication());
        String chosen = FormattedText.part(value, FormattedText.FORMAT);
        String format = usable.stream().anyMatch(candidate -> candidate.id().equals(chosen))
                ? chosen : usable.getFirst().id();
        FormElement group = FormElement.of(ElementType.FIELDSET, context.elementName(delta))
                .label(delta == 0 ? context.label() : "")
                .description(delta == 0 ? context.instance().description() : "");
        if (withSummary(context)) {
            FormElement summary = FormElement.of(ElementType.TEXTAREA, partName(context, delta, FormattedText.SUMMARY))
                    .label("Summary")
                    .description("Shown where the full text is too long. Left empty, the start of the text is used.")
                    .value(FormattedText.part(value, FormattedText.SUMMARY))
                    .attribute("rows", "3");
            if (Boolean.TRUE.equals(context.instance().settings().get(FormattedText.REQUIRED_SUMMARY))) {
                summary.markRequired();
            }
            group.child(summary);
        }
        FormElement text = FormElement.of(textInput(), partName(context, delta, FormattedText.VALUE))
                .label("Text")
                .value(FormattedText.part(value, FormattedText.VALUE))
                .attribute(EDITOR_TARGET, "true")
                .attribute(EDITOR_FORMATS, editorFormats(usable))
                .attribute(EDITOR_UPLOAD, FieldWidgetPaths.EDITOR_UPLOAD);
        UploadLimits limits = editorImages.limits();
        text.attribute(EDITOR_IMAGE_PREFIX + "extensions", String.join(" ", limits.extensions()))
                .attribute(EDITOR_IMAGE_PREFIX + "max-filesize", String.valueOf(limits.maxFilesize()))
                .attribute(EDITOR_IMAGE_PREFIX + "message-extension", limits.extensionMessage())
                .attribute(EDITOR_IMAGE_PREFIX + "message-size", limits.sizeMessage());
        if (delta == 0 && context.required()) {
            text.markRequired();
        }
        return group.child(text).child(FormElement.of(ElementType.SELECT, partName(context, delta, FormattedText.FORMAT))
                .label("Text format")
                .markRequired()
                .value(format)
                .attribute(FORMAT_SELECTOR, "true")
                .options(usable.stream().map(option -> new SelectOption(option.id(), option.label())).toList()));
    }

    /** The text with its format, and its summary when the widget asks for one, or nothing when the text is empty. */
    @Override
    public Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        String text = entered(context, delta, FormattedText.VALUE, submitted);
        if (text.isBlank()) {
            return null;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(FormattedText.VALUE, text);
        value.put(FormattedText.FORMAT, entered(context, delta, FormattedText.FORMAT, submitted));
        if (withSummary(context)) {
            value.put(FormattedText.SUMMARY, entered(context, delta, FormattedText.SUMMARY, submitted));
        }
        return value;
    }

    private static String entered(WidgetContext context, int delta, String part, Map<String, Object> submitted) {
        Object value = submitted.get(partName(context, delta, part));
        return (value == null) ? "" : value.toString();
    }

    private String editorFormats(List<TextFormat> usable) {
        Map<String, List<String>> tags = new LinkedHashMap<>();
        usable.forEach(format -> tags.put(format.id(), formats.editorTags(format)));
        return objectMapper.writeValueAsString(tags);
    }
}
