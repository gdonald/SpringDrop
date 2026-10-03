package dev.springdrop.kernel.media.sources;

import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.media.MediaIcons;
import dev.springdrop.kernel.media.MediaSource;
import dev.springdrop.kernel.media.MediaSourceException;
import dev.springdrop.kernel.media.SourceMetadata;
import dev.springdrop.kernel.media.oembed.OEmbedClient;
import dev.springdrop.kernel.media.oembed.OEmbedFormatter;
import dev.springdrop.kernel.media.oembed.OEmbedProvider;
import dev.springdrop.kernel.media.oembed.OEmbedResource;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A video on YouTube or Vimeo, held as its address. Its name is the title the
 * provider gives, and its thumbnail the provider's, downloaded and stored as a
 * public file, or the video icon when the provider names none.
 */
@SpringDropPlugin(id = RemoteVideoSource.ID, type = MediaSource.class)
public class RemoteVideoSource implements MediaSource {

    public static final String ID = "oembed:video";

    /** The longest address the source field holds. */
    public static final int MAX_ADDRESS_LENGTH = 2048;

    private static final Pattern IMAGE_EXTENSION = Pattern.compile("(?i)\\.(png|gif|jpe?g)(?:[?#].*)?$");

    private final OEmbedClient oembed;
    private final FileService files;
    private final MediaIcons icons;

    public RemoteVideoSource(OEmbedClient oembed, FileService files, MediaIcons icons) {
        this.oembed = oembed;
        this.files = files;
        this.icons = icons;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Remote video";
    }

    @Override
    public String description() {
        return "A video on YouTube or Vimeo, given by its address.";
    }

    @Override
    public String sourceFieldType() {
        return "string";
    }

    @Override
    public Map<String, Object> sourceFieldStorageSettings() {
        return Map.of(FieldSettings.MAX_LENGTH, MAX_ADDRESS_LENGTH);
    }

    @Override
    public Map<String, Object> sourceFieldInstanceSettings() {
        return Map.of();
    }

    @Override
    public String sourceFormatter() {
        return OEmbedFormatter.ID;
    }

    @Override
    public SourceMetadata metadata(Object sourceValue) throws IOException {
        String address = String.valueOf(sourceValue).strip();
        OEmbedResource resource = oembed.fetch(address);
        if (!resource.type().equals("video")) {
            throw new MediaSourceException("The address is not a video.");
        }
        OEmbedProvider provider = oembed.provider(address).orElseThrow();
        long thumbnail = resource.thumbnailUrl().isBlank() ? icons.icon(MediaIcons.VIDEO)
                : stored(oembed.thumbnail(provider, resource.thumbnailUrl()), resource.thumbnailUrl());
        return new SourceMetadata(resource.title().isBlank() ? address : resource.title(), thumbnail);
    }

    private long stored(byte[] image, String thumbnailUrl) throws IOException {
        Matcher extension = IMAGE_EXTENSION.matcher(thumbnailUrl);
        String name = "remote-video." + (extension.find() ? extension.group(1).toLowerCase(Locale.ROOT) : "jpg");
        return files.store(image, name, FileSchemes.PUBLIC, 0L).id();
    }
}
