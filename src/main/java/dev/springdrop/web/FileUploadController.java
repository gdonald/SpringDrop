package dev.springdrop.web;

import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileWidget;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.file.UploadLimits;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

/**
 * Takes a file picked in a file or image field's widget. The file is checked
 * against the field's limits, stored as a temporary file owned by the person
 * uploading it, and answered with the inputs for it, which the widget adds to
 * the form. It becomes permanent when the form is saved with it, and is removed
 * by cron if the form never is. A refused file is answered with the reason, as
 * plain text, and nothing is kept.
 */
@Controller
public class FileUploadController {

    public static final String FILE = "file";

    public static final String DELTA = "delta";

    private final FieldWidgetManager widgets;
    private final FileService files;
    private final FormRenderer renderer;

    public FileUploadController(FieldWidgetManager widgets, FileService files, FormRenderer renderer) {
        this.widgets = widgets;
        this.files = files;
        this.renderer = renderer;
    }

    @PostMapping(FileWidget.UPLOAD_PATH)
    public ResponseEntity<String> upload(
            @RequestParam(FieldWidgetController.ENTITY_TYPE) String entityTypeId,
            @RequestParam(FieldWidgetController.BUNDLE) String bundle,
            @RequestParam(FieldWidgetController.FIELD) String fieldName,
            @RequestParam(name = DELTA, defaultValue = "0") int delta,
            @RequestParam(FILE) MultipartFile upload,
            Authentication authentication) throws IOException {

        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal account)) {
            return refused(HttpStatus.FORBIDDEN, "Sign in to upload files.");
        }
        Optional<WidgetContext> found = fileField(entityTypeId, bundle, fieldName);
        if (found.isEmpty()) {
            return refused(HttpStatus.NOT_FOUND, "There is no such file field.");
        }
        WidgetContext context = found.get();
        FileWidget widget = (FileWidget) widgets.widget(context);
        UploadLimits limits = widget.limits(context);
        long size = upload.getSize();
        Optional<String> refusal = limits.violation(FileService.safeName(upload.getOriginalFilename()), size);
        if (refusal.isPresent()) {
            return refused(HttpStatus.UNPROCESSABLE_CONTENT, refusal.get());
        }

        String scheme = String.valueOf(context.storage().settings().getOrDefault(FileItem.URI_SCHEME,
                FileSchemes.PUBLIC));
        ManagedFile stored;
        try (InputStream content = upload.getInputStream()) {
            stored = files.store(content, upload.getOriginalFilename(), size, scheme, account.id());
        }
        Optional<String> imageRefusal = limits.imageViolation(() -> files.imageSize(stored));
        if (imageRefusal.isPresent()) {
            files.delete(stored.id());
            return refused(HttpStatus.UNPROCESSABLE_CONTENT, imageRefusal.get());
        }

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(renderer.render(
                widget.item(context, Math.max(0, delta), Map.of(FileItem.TARGET_ID, stored.id()))));
    }

    private Optional<WidgetContext> fileField(String entityTypeId, String bundle, String fieldName) {
        try {
            WidgetContext context = widgets.context(entityTypeId, bundle, fieldName);
            FieldWidget widget = widgets.widget(context);
            return (widget instanceof FileWidget) ? Optional.of(context) : Optional.empty();
        } catch (IllegalArgumentException missing) {
            return Optional.empty();
        }
    }

    private static ResponseEntity<String> refused(HttpStatus status, String reason) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(reason);
    }
}
