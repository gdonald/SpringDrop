package dev.springdrop.kernel.media.sources;

import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.media.MediaIcons;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.io.IOException;

/** A document, such as a PDF or a spreadsheet, uploaded to the site. */
@SpringDropPlugin(id = DocumentSource.ID, type = MediaSource.class)
public class DocumentSource extends FileBasedSource {

    public static final String ID = "file";

    private final MediaIcons icons;

    public DocumentSource(FileService files, MediaIcons icons) {
        super(files);
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Document";
    }

    @Override
    public String description() {
        return "A document, such as a PDF or a spreadsheet, uploaded to the site.";
    }

    @Override
    String extensions() {
        return "txt rtf pdf doc docx odt xls xlsx ods ppt pptx odp csv";
    }

    @Override
    long thumbnail(ManagedFile file) throws IOException {
        return icons.icon(MediaIcons.DOCUMENT);
    }
}
