package dev.springdrop.web;

import dev.springdrop.kernel.file.FileAccess;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Serves an image drawn with a style, drawing it first when it has not been
 * drawn yet or its original has changed since. A public image's derivatives are
 * served at {@code /files/styles/...} to anyone, and a private image's at
 * {@code /system/files/styles/...} to whoever may download the original.
 */
@Controller
public class ImageDerivativeController {

    private static final String PUBLIC_PATTERN = FileSchemes.PUBLIC_URL_PREFIX + "/"
            + ImageStyleManager.STYLES_DIRECTORY + "/{style}/{scheme}/**";

    private static final String PRIVATE_PATTERN = FileService.PRIVATE_URL_PREFIX + "/"
            + ImageStyleManager.STYLES_DIRECTORY + "/{style}/{scheme}/**";

    /** How long a browser or shared cache keeps a public derivative before asking again. */
    private static final Duration PUBLIC_MAX_AGE = Duration.ofDays(1);

    private final ImageStyleManager styles;
    private final FileService files;
    private final FileAccess access;

    public ImageDerivativeController(ImageStyleManager styles, FileService files, FileAccess access) {
        this.styles = styles;
        this.files = files;
        this.access = access;
    }

    @GetMapping({PUBLIC_PATTERN, PRIVATE_PATTERN})
    public ResponseEntity<InputStreamResource> derivative(
            @PathVariable String style,
            @PathVariable String scheme,
            @RequestParam(name = ImageStyleManager.TOKEN_PARAMETER, required = false) String token,
            HttpServletRequest request,
            Authentication authentication) throws IOException {

        String uri = request.getRequestURI().substring(request.getContextPath().length());
        boolean publicAddress = uri.startsWith(FileSchemes.PUBLIC_URL_PREFIX + "/");
        String prefix = (publicAddress ? FileSchemes.PUBLIC_URL_PREFIX : FileService.PRIVATE_URL_PREFIX) + "/"
                + ImageStyleManager.STYLES_DIRECTORY + "/" + style + "/" + scheme + "/";
        Optional<ImageStyle> found = styles.find(style);
        Optional<ManagedFile> source = files.findByUri(scheme + "://" + uri.substring(prefix.length()));
        if (found.isEmpty() || source.isEmpty() || publicAddress != scheme.equals(FileSchemes.PUBLIC)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        ManagedFile file = source.get();
        if (!publicAddress && !access.mayDownload(file, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!styles.upToDate(found.get(), file)) {
            if (!styles.validToken(style, file.uri(), token)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            try {
                styles.draw(found.get(), file);
            } catch (IOException notAnImage) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mime()))
                .cacheControl(publicAddress ? CacheControl.maxAge(PUBLIC_MAX_AGE).cachePublic()
                        : CacheControl.noCache().cachePrivate())
                .body(new InputStreamResource(styles.read(found.get(), file)));
    }
}
