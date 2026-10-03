package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.widget.MultipleValueWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.util.unit.DataSize;

/**
 * Edits every file of a file or image field in one place: the files already
 * attached, each with its own inputs, an order, and a remove choice, and a file
 * input that uploads new files as they are picked. The file input carries the
 * field's limits so the browser checks a file before sending it, with the same
 * messages the server gives. Uploading needs JavaScript; the attached files can
 * be edited, reordered, and removed without it.
 */
public abstract class FileWidget implements MultipleValueWidget {

    /** Where the widget sends a picked file. */
    public static final String UPLOAD_PATH = "/file/upload";

    public static final String WEIGHT = "weight";

    public static final String REMOVE = "remove";

    public static final String UPLOAD = "upload";

    private final FileService files;
    private final DataSize siteMaxFilesize;

    protected FileWidget(FileService files, DataSize siteMaxFilesize) {
        this.files = files;
        this.siteMaxFilesize = siteMaxFilesize;
    }

    protected FileService files() {
        return files;
    }

    /** The name one part of the file at one position is submitted under. */
    public static String partName(WidgetContext context, int delta, String part) {
        return context.fieldName() + "[" + delta + "]:" + part;
    }

    /** The inputs a widget asks for beside the file, such as a description or alternative text. */
    protected abstract List<FormElement> valueInputs(WidgetContext context, int delta, Object value);

    /** The value one attached file is saved as, given what was entered beside it. */
    protected abstract Map<String, Object> valueOf(WidgetContext context, int delta, ManagedFile file,
            Map<String, Object> submitted);

    /** What the file input asks for. */
    protected abstract String uploadLabel();

    @Override
    public FormElement elementForAll(WidgetContext context, List<Object> values) {
        FormElement items = FormElement.of(ElementType.CONTAINER, context.fieldName() + ":items")
                .attribute("class", "list-group mb-2")
                .attribute("data-file-items", "true");
        for (int delta = 0; delta < values.size(); delta++) {
            items.child(item(context, delta, values.get(delta)));
        }
        return FormElement.of(ElementType.FIELDSET, context.fieldName())
                .label(context.label())
                .description(context.instance().description())
                .child(items)
                .child(upload(context));
    }

    /** The inputs for one attached file. */
    public FormElement item(WidgetContext context, int delta, Object value) {
        Optional<ManagedFile> file = FileItem.fileId(value).flatMap(files::find);
        FormElement name = FormElement.of(ElementType.TEXT, partName(context, delta, "name"))
                .label(file.map(ManagedFile::filename).orElse("This file is no longer stored."));
        file.filter(stored -> stored.mime().startsWith("image/"))
                .ifPresent(image -> name.attribute("data-file-preview", files.url(image)));
        FormElement item = FormElement.of(ElementType.CONTAINER, partName(context, delta, "item"))
                .attribute("class", "list-group-item")
                .attribute("data-file-item", String.valueOf(delta))
                .attribute("draggable", "true")
                .child(FormElement.of(ElementType.HIDDEN, partName(context, delta, FileItem.TARGET_ID))
                        .value(FileItem.fileId(value).map(String::valueOf).orElse("")))
                .child(name);
        valueInputs(context, delta, value).forEach(item::child);
        return item
                .child(FormElement.of(ElementType.NUMBER, partName(context, delta, WEIGHT))
                        .label("Order")
                        .value(delta)
                        .attribute("data-file-weight", "true"))
                .child(FormElement.of(ElementType.CHECKBOX, partName(context, delta, REMOVE))
                        .label("Remove")
                        .attribute("data-file-remove", "true"));
    }

    private FormElement upload(WidgetContext context) {
        UploadLimits limits = limits(context);
        FormElement input = FormElement.of(ElementType.FILE, context.fieldName() + ":" + UPLOAD)
                .label(uploadLabel())
                .description(limitsDescription(limits))
                .attribute("data-file-upload", "true")
                .attribute("data-upload-url", UPLOAD_PATH)
                .attribute("data-entity-type", context.instance().entityTypeId())
                .attribute("data-bundle", context.instance().bundle())
                .attribute("data-field", context.fieldName())
                .attribute("data-file-extensions", String.join(" ", limits.extensions()))
                .attribute("data-dangerous-extensions", String.join(" ", FileService.DANGEROUS_EXTENSIONS.stream()
                        .sorted().toList()))
                .attribute("data-max-filesize", String.valueOf(limits.maxFilesize()))
                .attribute("data-cardinality", String.valueOf(context.unlimited() ? -1 : context.cardinality()))
                .attribute("data-message-extension", limits.extensionMessage())
                .attribute("data-message-size", limits.sizeMessage());
        if (!limits.extensions().isEmpty()) {
            input.attribute("accept", limits.extensions().stream().map(extension -> "." + extension)
                    .collect(Collectors.joining(",")));
        }
        if (limits.image()) {
            input.attribute("data-image", "true")
                    .attribute("data-min-resolution", limits.minResolution())
                    .attribute("data-max-resolution", limits.maxResolution())
                    .attribute("data-message-not-image", UploadLimits.notAnImageMessage())
                    .attribute("data-message-too-small", limits.tooSmallMessage())
                    .attribute("data-message-too-large", limits.tooLargeMessage());
        }
        if (context.cardinality() != 1) {
            input.attribute("multiple", "multiple");
        }
        return input;
    }

    /** The field's limits, with the size held to the site's largest upload. */
    public UploadLimits limits(WidgetContext context) {
        return UploadLimits.of(context.storage(), context.instance()).cappedAt(siteMaxFilesize.toBytes());
    }

    private static String limitsDescription(UploadLimits limits) {
        String kinds = limits.extensions().isEmpty() ? "Any kind of file"
                : "Allowed: " + String.join(" ", limits.extensions());
        return kinds + ". Up to " + FileFormatter.readableSize(limits.maxFilesize()) + ".";
    }

    @Override
    public List<Object> extractAll(WidgetContext context, Map<String, Object> submitted) {
        Pattern target = Pattern.compile(Pattern.quote(context.fieldName()) + "\\[(\\d{1,4})]:" + FileItem.TARGET_ID);
        record Attached(int delta, int weight, ManagedFile file) {
        }
        List<Attached> attached = new ArrayList<>();
        for (Map.Entry<String, Object> entry : submitted.entrySet()) {
            Matcher matcher = target.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            int delta = Integer.parseInt(matcher.group(1));
            boolean removed = FormRenderer.CHECKED_VALUE.equals(
                    String.valueOf(submitted.get(partName(context, delta, REMOVE))));
            Optional<ManagedFile> file = FileItem.fileId(Map.of(FileItem.TARGET_ID, String.valueOf(entry.getValue())))
                    .flatMap(files::find);
            if (!removed && file.isPresent()) {
                attached.add(new Attached(delta, weight(context, delta, submitted), file.get()));
            }
        }
        attached.sort(Comparator.comparingInt(Attached::weight).thenComparingInt(Attached::delta));
        List<Object> values = new ArrayList<>();
        attached.forEach(each -> values.add(valueOf(context, each.delta(), each.file(), submitted)));
        return values;
    }

    private static int weight(WidgetContext context, int delta, Map<String, Object> submitted) {
        String weight = String.valueOf(submitted.get(partName(context, delta, WEIGHT)));
        return weight.matches("-?\\d{1,6}") ? Integer.parseInt(weight) : delta;
    }

    protected static String entered(WidgetContext context, int delta, String part, Map<String, Object> submitted) {
        Object value = submitted.get(partName(context, delta, part));
        return (value == null) ? "" : value.toString().strip();
    }

    protected static Map<String, Object> target(ManagedFile file) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(FileItem.TARGET_ID, file.id());
        return value;
    }
}
