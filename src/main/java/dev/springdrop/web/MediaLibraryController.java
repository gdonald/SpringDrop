package dev.springdrop.web;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.FileFieldType;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ImageFieldType;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.file.UploadLimits;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.media.MediaLibraryWidget;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * The media library a media reference field's widget opens: a tab per media
 * type the field may refer to, listing the media of that type the person may
 * view, newest change first and narrowed by name, and a form adding a new item
 * of the type for someone who may create it. Each listed item carries the
 * inputs the widget takes for it.
 */
@Controller
public class MediaLibraryController {

    public static final String ADD_PATH = MediaLibraryWidget.LIBRARY_PATH + "/add";

    public static final String TYPE = "type";

    public static final String SEARCH = "q";

    public static final String FILE = "file";

    public static final String ADDRESS = "address";

    public static final String NAME = "name";

    private static final org.springframework.http.MediaType PLAIN_TEXT = org.springframework.http.MediaType.TEXT_PLAIN;

    /** How many items a tab lists at once. */
    public static final int PAGE_SIZE = 24;

    private final FieldWidgetManager widgets;
    private final FieldConfigManager fields;
    private final MediaTypeManager types;
    private final MediaService media;
    private final EntityQueryExecutor queries;
    private final EntityAccessManager access;
    private final FileService files;
    private final FormRenderer renderer;
    private final MediaLibraryWidget widget;
    private final long siteMaxFilesize;

    public MediaLibraryController(FieldWidgetManager widgets, FieldConfigManager fields, MediaTypeManager types,
            MediaService media, EntityQueryExecutor queries, EntityAccessManager access, FileService files,
            FormRenderer renderer, MediaLibraryWidget widget,
            @Value("${spring.servlet.multipart.max-file-size:32MB}") DataSize siteMaxFilesize) {
        this.widgets = widgets;
        this.fields = fields;
        this.types = types;
        this.media = media;
        this.queries = queries;
        this.access = access;
        this.files = files;
        this.renderer = renderer;
        this.widget = widget;
        this.siteMaxFilesize = siteMaxFilesize.toBytes();
    }

    public record LibraryTab(String id, String label, String path, boolean active) {
    }

    public record LibraryItem(Object id, String label, String thumbnail, String inputs) {
    }

    @GetMapping(MediaLibraryWidget.LIBRARY_PATH)
    public String library(
            @RequestParam(FieldWidgetController.ENTITY_TYPE) String entityTypeId,
            @RequestParam(FieldWidgetController.BUNDLE) String bundle,
            @RequestParam(FieldWidgetController.FIELD) String fieldName,
            @RequestParam(name = TYPE, defaultValue = "") String typeId,
            @RequestParam(name = SEARCH, defaultValue = "") String search,
            Model model) {

        WidgetContext context = mediaField(entityTypeId, bundle, fieldName);
        List<MediaType> allowed = allowedTypes(context);
        MediaType chosen = allowed.stream().filter(type -> type.id().equals(typeId)).findFirst()
                .orElse(allowed.getFirst());
        String base = MediaLibraryWidget.libraryPath(context);
        model.addAttribute("tabs", allowed.stream().map(type -> new LibraryTab(type.id(), type.label(),
                base + "&" + TYPE + "=" + type.id(), type.id().equals(chosen.id()))).toList());
        model.addAttribute("items", items(context, chosen, search.strip()));
        model.addAttribute("context", Map.of(FieldWidgetController.ENTITY_TYPE, entityTypeId,
                FieldWidgetController.BUNDLE, bundle, FieldWidgetController.FIELD, fieldName, TYPE, chosen.id()));
        model.addAttribute("search", search.strip());
        model.addAttribute("libraryPath", MediaLibraryWidget.LIBRARY_PATH);
        model.addAttribute("addPath", ADD_PATH);
        model.addAttribute("mayCreate", access.may(MediaEntityType.ID, chosen.id(), EntityAccessHandler.CREATE));
        model.addAttribute("remote", !isFileSource(media.sourceOf(chosen)));
        model.addAttribute("accept", acceptOf(chosen));
        return "media/library";
    }

    private List<LibraryItem> items(WidgetContext context, MediaType type, String search) {
        var query = queries.query(MediaEntityType.ID)
                .condition(Condition.equal(MediaEntityType.BUNDLE_KEY, type.id()))
                .sort(Sort.descending(BaseFieldDefinition.CHANGED))
                .range(0, PAGE_SIZE);
        if (!search.isEmpty()) {
            query.condition(Condition.contains("label", search));
        }
        return query.ids().stream()
                .map(widget::viewable)
                .flatMap(Optional::stream)
                .map(item -> libraryItem(context, item))
                .toList();
    }

    private LibraryItem libraryItem(WidgetContext context, EntityData item) {
        return new LibraryItem(item.id(), item.label(), widget.thumbnail(item).orElse(""),
                renderer.render(widget.item(context, MediaLibraryWidget.DELTA_PLACEHOLDER, item)));
    }

    /**
     * Adds a media item of one of the field's types from an uploaded file or a
     * remote address, and answers with it as a library item, chosen. A file is
     * held to the type's source field limits. A refusal is answered with its
     * reason, and an uploaded file is not kept.
     */
    @PostMapping(ADD_PATH)
    public Object add(
            @RequestParam(FieldWidgetController.ENTITY_TYPE) String entityTypeId,
            @RequestParam(FieldWidgetController.BUNDLE) String bundle,
            @RequestParam(FieldWidgetController.FIELD) String fieldName,
            @RequestParam(TYPE) String typeId,
            @RequestParam(name = FILE, required = false) MultipartFile upload,
            @RequestParam(name = ADDRESS, defaultValue = "") String address,
            @RequestParam(name = NAME, defaultValue = "") String name,
            Authentication authentication,
            Model model) throws IOException {

        WidgetContext context = mediaField(entityTypeId, bundle, fieldName);
        Optional<MediaType> type = allowedTypes(context).stream().filter(each -> each.id().equals(typeId))
                .findFirst();
        if (type.isEmpty() || !(authentication != null && authentication.getPrincipal() instanceof AccountPrincipal)
                || !access.may(MediaEntityType.ID, typeId, EntityAccessHandler.CREATE)) {
            return refused(HttpStatus.FORBIDDEN, "You may not add media of this type here.");
        }
        if (name.strip().length() > MediaService.MAX_NAME_LENGTH) {
            return refused(HttpStatus.UNPROCESSABLE_CONTENT,
                    "The name is longer than " + MediaService.MAX_NAME_LENGTH + " characters.");
        }
        long owner = ((AccountPrincipal) authentication.getPrincipal()).id();
        MediaSource source = media.sourceOf(type.get());
        Optional<ManagedFile> stored = Optional.empty();
        Object sourceValue;
        if (isFileSource(source)) {
            if (upload == null || upload.isEmpty()) {
                return refused(HttpStatus.UNPROCESSABLE_CONTENT, "Choose a file to upload.");
            }
            FieldStorageConfig storage = fields.findStorage(MediaEntityType.ID, type.get().sourceField())
                    .orElseThrow();
            UploadLimits limits = UploadLimits.of(storage, fields.findInstance(MediaEntityType.ID, typeId,
                    type.get().sourceField()).orElseThrow()).cappedAt(siteMaxFilesize);
            Optional<String> refusal = limits.violation(FileService.safeName(upload.getOriginalFilename()),
                    upload.getSize());
            if (refusal.isPresent()) {
                return refused(HttpStatus.UNPROCESSABLE_CONTENT, refusal.get());
            }
            try (InputStream content = upload.getInputStream()) {
                stored = Optional.of(files.store(content, upload.getOriginalFilename(), upload.getSize(),
                        String.valueOf(storage.settings().getOrDefault(FileItem.URI_SCHEME, FileSchemes.PUBLIC)),
                        owner));
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put(FileItem.TARGET_ID, stored.get().id());
            if (source.sourceFieldType().equals(ImageFieldType.ID)) {
                value.put(FileItem.ALT, name.isBlank() ? stored.get().filename() : name.strip());
            }
            sourceValue = value;
        } else {
            sourceValue = address.strip();
        }
        EntityData blank = media.create(type.get(), owner);
        Map<String, Object> values = new LinkedHashMap<>(blank.fields());
        values.put(type.get().sourceField(), sourceValue);
        EntityData saved;
        try {
            saved = media.save(new EntityData(MediaEntityType.ID, null, null, typeId, name.strip(),
                    blank.langcode(), null, values));
        } catch (EntityValidationException invalid) {
            stored.ifPresent(file -> files.delete(file.id()));
            return refused(HttpStatus.UNPROCESSABLE_CONTENT, invalid.violations().getFirst().message());
        }
        model.addAttribute("item", libraryItem(context, saved));
        model.addAttribute("checked", true);
        return "media/library-item";
    }

    private static ResponseEntity<String> refused(HttpStatus status, String reason) {
        return ResponseEntity.status(status).contentType(PLAIN_TEXT).body(reason);
    }

    private static boolean isFileSource(MediaSource source) {
        return source.sourceFieldType().equals(FileFieldType.ID) || source.sourceFieldType().equals(ImageFieldType.ID);
    }

    private String acceptOf(MediaType type) {
        return fields.findInstance(MediaEntityType.ID, type.id(), type.sourceField())
                .map(instance -> String.join(",", FileItem.extensions(instance.settings()
                        .get(FileItem.FILE_EXTENSIONS)).stream().map(extension -> "." + extension).toList()))
                .orElse("");
    }

    /** The field's widget context, for an entity reference field to media. */
    private WidgetContext mediaField(String entityTypeId, String bundle, String fieldName) {
        try {
            WidgetContext context = widgets.context(entityTypeId, bundle, fieldName);
            if (context.storage().type().equals(EntityReferenceFieldType.ID)
                    && EntityReferenceFieldType.targetType(context.storage()).equals(MediaEntityType.ID)
                    && !allowedTypes(context).isEmpty()) {
                return context;
            }
        } catch (IllegalArgumentException missing) {
            // answered as not found below
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no such media field.");
    }

    private List<MediaType> allowedTypes(WidgetContext context) {
        List<MediaType> all = types.all();
        List<String> allowed = MediaLibraryWidget.allowedTypes(context, all.stream().map(MediaType::id).toList());
        return all.stream().filter(type -> allowed.contains(type.id())).toList();
    }
}
