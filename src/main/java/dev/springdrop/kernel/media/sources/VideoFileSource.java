package dev.springdrop.kernel.media.sources;

import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.media.MediaIcons;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.io.IOException;

/** A video file uploaded to the site. */
@SpringDropPlugin(id = VideoFileSource.ID, type = MediaSource.class)
public class VideoFileSource extends FileBasedSource {

    public static final String ID = "video_file";

    private final MediaIcons icons;

    public VideoFileSource(FileService files, MediaIcons icons) {
        super(files);
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Video file";
    }

    @Override
    public String description() {
        return "A video file uploaded to the site.";
    }

    @Override
    String extensions() {
        return "mp4 webm ogv mov";
    }

    @Override
    long thumbnail(ManagedFile file) throws IOException {
        return icons.icon(MediaIcons.VIDEO);
    }
}
