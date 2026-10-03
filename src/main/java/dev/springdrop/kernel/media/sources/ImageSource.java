package dev.springdrop.kernel.media.sources;

import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ImageFieldType;
import dev.springdrop.kernel.file.ImageFormatter;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;

/** An uploaded image, which is its own thumbnail. */
@SpringDropPlugin(id = ImageSource.ID, type = MediaSource.class)
public class ImageSource extends FileBasedSource {

    public static final String ID = "image";

    public ImageSource(FileService files) {
        super(files);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Image";
    }

    @Override
    public String description() {
        return "An image uploaded to the site.";
    }

    @Override
    public String sourceFieldType() {
        return ImageFieldType.ID;
    }

    @Override
    String extensions() {
        return ImageFieldType.DEFAULT_EXTENSIONS;
    }

    @Override
    public Map<String, Object> sourceFieldInstanceSettings() {
        return Map.of(FileItem.FILE_EXTENSIONS, extensions(), FileItem.MAX_FILESIZE, 0L,
                FileItem.ALT_FIELD, true, FileItem.ALT_FIELD_REQUIRED, true, FileItem.TITLE_FIELD, false,
                FileItem.MIN_RESOLUTION, "", FileItem.MAX_RESOLUTION, "");
    }

    @Override
    public String sourceFormatter() {
        return ImageFormatter.ID;
    }

    @Override
    long thumbnail(ManagedFile file) {
        return file.id();
    }
}
