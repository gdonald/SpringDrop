package dev.springdrop.web;

import dev.springdrop.kernel.field.widget.FieldWidgetPaths;
import dev.springdrop.kernel.file.EditorImages;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.file.UploadLimits;
import dev.springdrop.kernel.filter.TextFormat;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
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
 * Takes an image an editor inserts into formatted text. The person has to be
 * signed in, allowed to write in the format, and the format has to keep
 * images. The image is checked like an image field's upload, stored as a
 * public temporary file owned by the uploader, and answered with its id,
 * address, and size as JSON. It stays once text saved with it refers to it.
 */
@Controller
public class EditorUploadController {

    public static final String FORMAT = "format";

    public static final String FILE = "file";

    private final TextFormatManager formats;
    private final FileService files;
    private final UploadLimits limits;

    public EditorUploadController(TextFormatManager formats, FileService files, EditorImages editorImages) {
        this.formats = formats;
        this.files = files;
        this.limits = editorImages.limits();
    }

    @PostMapping(FieldWidgetPaths.EDITOR_UPLOAD)
    public ResponseEntity<?> upload(
            @RequestParam(FORMAT) String formatId,
            @RequestParam(FILE) MultipartFile upload,
            Authentication authentication) throws IOException {

        if (authentication == null || !(authentication.getPrincipal() instanceof AccountPrincipal account)) {
            return refused(HttpStatus.FORBIDDEN, "Sign in to upload images.");
        }
        Optional<TextFormat> format = formats.find(formatId);
        if (format.isEmpty() || !formats.mayUse(format.get(), authentication)
                || !formats.editorTags(format.get()).contains("img")) {
            return refused(HttpStatus.FORBIDDEN, "This text format does not take images.");
        }
        Optional<String> refusal = limits.violation(FileService.safeName(upload.getOriginalFilename()),
                upload.getSize());
        if (refusal.isPresent()) {
            return refused(HttpStatus.UNPROCESSABLE_CONTENT, refusal.get());
        }
        ManagedFile stored;
        try (InputStream content = upload.getInputStream()) {
            stored = files.store(content, upload.getOriginalFilename(), upload.getSize(), FileSchemes.PUBLIC,
                    account.id());
        }
        var size = files.imageSize(stored);
        if (size.isEmpty()) {
            files.delete(stored.id());
            return refused(HttpStatus.UNPROCESSABLE_CONTENT, UploadLimits.notAnImageMessage());
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", stored.id());
        answer.put("url", files.url(stored));
        answer.put("width", size.get().width());
        answer.put("height", size.get().height());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(answer);
    }

    private static ResponseEntity<String> refused(HttpStatus status, String reason) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(reason);
    }
}
