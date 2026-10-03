package dev.springdrop.kernel.media.sources;

import dev.springdrop.kernel.file.FileFieldType;
import dev.springdrop.kernel.file.FileFormatter;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.media.MediaSourceException;
import dev.springdrop.kernel.media.SourceMetadata;
import java.io.IOException;
import java.util.Map;

/** A source whose item is an uploaded file of some kinds, named after the file. */
abstract class FileBasedSource implements MediaSource {

    private final FileService files;

    FileBasedSource(FileService files) {
        this.files = files;
    }

    /** The extensions the source field takes, separated by spaces. */
    abstract String extensions();

    /** The managed image file standing for the uploaded file. */
    abstract long thumbnail(ManagedFile file) throws IOException;

    @Override
    public String sourceFieldType() {
        return FileFieldType.ID;
    }

    @Override
    public Map<String, Object> sourceFieldStorageSettings() {
        return Map.of(FileItem.URI_SCHEME, FileSchemes.PUBLIC);
    }

    @Override
    public Map<String, Object> sourceFieldInstanceSettings() {
        return Map.of(FileItem.FILE_EXTENSIONS, extensions(), FileItem.MAX_FILESIZE, 0L,
                FileItem.DESCRIPTION_FIELD, false);
    }

    @Override
    public String sourceFormatter() {
        return FileFormatter.ID;
    }

    @Override
    public SourceMetadata metadata(Object sourceValue) throws IOException {
        ManagedFile file = storedFile(files, sourceValue);
        return new SourceMetadata(file.filename(), thumbnail(file));
    }

    static ManagedFile storedFile(FileService files, Object sourceValue) {
        return FileItem.fileId(sourceValue).flatMap(files::find)
                .orElseThrow(() -> new MediaSourceException("Choose a file that has been uploaded."));
    }
}
