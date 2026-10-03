package dev.springdrop.web;

import dev.springdrop.kernel.file.FileAccess;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves private files, each only to someone {@link FileAccess} lets download
 * it. A path that names no stored private file is not found. A file is sent
 * with its stored kind and name, and marked private so a shared cache keeps no
 * copy.
 */
@Controller
public class PrivateFileController {

    private final FileService files;
    private final FileAccess access;

    public PrivateFileController(FileService files, FileAccess access) {
        this.files = files;
        this.access = access;
    }

    @GetMapping(FileService.PRIVATE_URL_PREFIX + "/**")
    public ResponseEntity<InputStreamResource> download(HttpServletRequest request, Authentication authentication)
            throws IOException {

        String prefix = request.getContextPath() + FileService.PRIVATE_URL_PREFIX + "/";
        String path = request.getRequestURI().substring(prefix.length());
        Optional<ManagedFile> found = files.findByUri(FileSchemes.PRIVATE + "://" + path);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        ManagedFile file = found.get();
        if (!access.mayDownload(file, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mime()))
                .cacheControl(CacheControl.noCache().cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(file.filename()).build()
                        .toString())
                .body(new InputStreamResource(files.read(file)));
    }
}
