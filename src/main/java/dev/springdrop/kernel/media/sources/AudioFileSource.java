package dev.springdrop.kernel.media.sources;

import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.media.MediaIcons;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.io.IOException;

/** An audio file uploaded to the site. */
@SpringDropPlugin(id = AudioFileSource.ID, type = MediaSource.class)
public class AudioFileSource extends FileBasedSource {

    public static final String ID = "audio_file";

    private final MediaIcons icons;

    public AudioFileSource(FileService files, MediaIcons icons) {
        super(files);
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Audio file";
    }

    @Override
    public String description() {
        return "An audio file uploaded to the site.";
    }

    @Override
    String extensions() {
        return "mp3 wav aac ogg oga m4a";
    }

    @Override
    long thumbnail(ManagedFile file) throws IOException {
        return icons.icon(MediaIcons.AUDIO);
    }
}
