package dev.springdrop.kernel.media;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.MultipleValueWidget;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks media for a reference field from the media library. The media already
 * chosen are listed with their thumbnails, an order, and a remove choice, and a
 * button opens the library, where existing media are chosen or new media
 * uploaded. Only media the person may view are listed or kept on save. Without
 * JavaScript the button opens the library as a page and the chosen media can
 * still be reordered and removed.
 */
@SpringDropPlugin(id = MediaLibraryWidget.ID, type = FieldWidget.class)
public class MediaLibraryWidget implements MultipleValueWidget {

    public static final String ID = "media_library";

    /** Where the library is served. */
    public static final String LIBRARY_PATH = "/media-library";

    public static final String WEIGHT = "weight";

    public static final String REMOVE = "remove";

    /** Stands for the position in an item's names until the browser places it. */
    public static final String DELTA_PLACEHOLDER = "__delta__";

    private final MediaService media;
    private final EntityAccessManager access;
    private final FileService files;
    private final ImageStyleManager imageStyles;

    public MediaLibraryWidget(MediaService media, EntityAccessManager access, FileService files,
            ImageStyleManager imageStyles) {
        this.media = media;
        this.access = access;
        this.files = files;
        this.imageStyles = imageStyles;
    }

    @Override
    public String id() {
        return ID;
    }

    public static String partName(WidgetContext context, String delta, String part) {
        return context.fieldName() + "[" + delta + "]:" + part;
    }

    /** The library's address for a field. */
    public static String libraryPath(WidgetContext context) {
        return LIBRARY_PATH + "?entity_type=" + encode(context.instance().entityTypeId()) + "&bundle="
                + encode(context.instance().bundle()) + "&field=" + encode(context.fieldName());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public FormElement elementForAll(WidgetContext context, List<Object> values) {
        FormElement items = FormElement.of(ElementType.CONTAINER, context.fieldName() + ":items")
                .attribute("class", "list-group mb-2")
                .attribute("data-media-items", "true");
        int delta = 0;
        for (Object value : values) {
            Optional<EntityData> item = viewable(value);
            if (item.isPresent()) {
                items.child(item(context, String.valueOf(delta++), item.get()));
            }
        }
        return FormElement.of(ElementType.FIELDSET, context.fieldName())
                .label(context.label())
                .description(context.instance().description())
                .attribute("data-media-field", context.fieldName())
                .attribute("data-cardinality", String.valueOf(context.unlimited() ? -1 : context.cardinality()))
                .child(items)
                .child(FormElement.of(ElementType.LINK, context.fieldName() + ":library")
                        .label("Add media")
                        .value(libraryPath(context))
                        .attribute("data-media-library-open", "true")
                        .attribute("target", "_blank"));
    }

    /** The inputs for one chosen media item at a position, or at the placeholder for the browser to fill in. */
    public FormElement item(WidgetContext context, String delta, EntityData item) {
        FormElement name = FormElement.of(ElementType.TEXT, partName(context, delta, "name")).label(item.label());
        thumbnail(item).ifPresent(address -> name.attribute("data-media-thumbnail", address));
        return FormElement.of(ElementType.CONTAINER, partName(context, delta, "item"))
                .attribute("class", "list-group-item")
                .attribute("data-media-item", String.valueOf(item.id()))
                .attribute("draggable", "true")
                .child(FormElement.of(ElementType.HIDDEN, partName(context, delta, "target_id"))
                        .value(String.valueOf(item.id())))
                .child(name)
                .child(FormElement.of(ElementType.NUMBER, partName(context, delta, WEIGHT)).label("Order")
                        .value(delta.equals(DELTA_PLACEHOLDER) ? "0" : delta)
                        .attribute("data-media-weight", "true"))
                .child(FormElement.of(ElementType.CHECKBOX, partName(context, delta, REMOVE)).label("Remove")
                        .attribute("data-media-remove", "true"));
    }

    /** The address of a media item's thumbnail in the thumbnail image style, when it has one. */
    public Optional<String> thumbnail(EntityData item) {
        return Optional.ofNullable(item.fields().get(MediaEntityType.THUMBNAIL))
                .filter(Number.class::isInstance)
                .flatMap(id -> files.find(((Number) id).longValue()))
                .map(file -> imageStyles.find("thumbnail").map(style -> imageStyles.url(style.id(), file))
                        .orElseGet(() -> files.url(file)));
    }

    /** The media item a value refers to, when it exists and the person may view it. */
    public Optional<EntityData> viewable(Object value) {
        String id = String.valueOf(value);
        if (!id.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        return media.find(Long.parseLong(id))
                .filter(item -> access.may(MediaEntityType.ID, item, EntityAccessHandler.VIEW));
    }

    @Override
    public List<Object> extractAll(WidgetContext context, Map<String, Object> submitted) {
        Pattern target = Pattern.compile(Pattern.quote(context.fieldName()) + "\\[(\\d{1,4})]:target_id");
        record Chosen(int delta, int weight, Object id) {
        }
        List<Chosen> chosen = new ArrayList<>();
        for (Map.Entry<String, Object> entry : submitted.entrySet()) {
            Matcher matcher = target.matcher(entry.getKey());
            if (!matcher.matches()) {
                continue;
            }
            String delta = matcher.group(1);
            boolean removed = FormRenderer.CHECKED_VALUE.equals(String.valueOf(
                    submitted.get(partName(context, delta, REMOVE))));
            Optional<EntityData> item = viewable(entry.getValue());
            if (!removed && item.isPresent()) {
                String weight = String.valueOf(submitted.get(partName(context, delta, WEIGHT)));
                chosen.add(new Chosen(Integer.parseInt(delta),
                        weight.matches("-?\\d{1,6}") ? Integer.parseInt(weight) : Integer.parseInt(delta),
                        item.get().id()));
            }
        }
        chosen.sort(Comparator.comparingInt(Chosen::weight).thenComparingInt(Chosen::delta));
        return chosen.stream().map(Chosen::id).distinct().map(Object.class::cast).toList();
    }

    /** The media types a field may refer to: those its target bundles name, or all of them. */
    public static List<String> allowedTypes(WidgetContext context, List<String> allTypes) {
        Object named = context.instance().settings().get(EntityReferenceFieldType.TARGET_BUNDLES);
        List<String> bundles = (named instanceof List<?> list)
                ? list.stream().map(String::valueOf).filter(allTypes::contains).toList() : List.of();
        return bundles.isEmpty() ? allTypes : bundles;
    }
}
