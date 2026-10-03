package dev.springdrop.kernel.file;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * What an image inserted into formatted text through an editor is held to: the
 * image kinds an image field takes by default, no larger than the site's
 * largest upload. Editor images are kept in public files.
 */
@Component
public class EditorImages {

    private final UploadLimits limits;

    public EditorImages(@Value("${spring.servlet.multipart.max-file-size:32MB}") DataSize siteMaxFilesize) {
        this.limits = new UploadLimits(List.of(ImageFieldType.DEFAULT_EXTENSIONS.split(" ")), 0, true, "", "")
                .cappedAt(siteMaxFilesize.toBytes());
    }

    public UploadLimits limits() {
        return limits;
    }
}
