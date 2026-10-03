package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What a file or image field takes: the extensions allowed, the largest size in
 * bytes (0 for no limit beyond the site's), whether only images are taken, and
 * an image's bounds in pixels. Uploads are checked against these as they
 * arrive, and saved values again on save, with the same messages either way,
 * and the upload widget hands them to the browser to check before sending.
 */
public record UploadLimits(
        List<String> extensions, long maxFilesize, boolean image, String minResolution, String maxResolution) {

    public UploadLimits {
        extensions = List.copyOf(extensions);
    }

    /** The limits a field's instance settings give. */
    public static UploadLimits of(FieldStorageConfig storage, FieldInstanceConfig instance) {
        Map<String, Object> settings = instance.settings();
        boolean image = storage.type().equals(ImageFieldType.ID);
        return new UploadLimits(
                FileItem.extensions(settings.get(FileItem.FILE_EXTENSIONS)),
                FileItem.maxFilesize(settings.get(FileItem.MAX_FILESIZE)),
                image,
                image ? String.valueOf(settings.getOrDefault(FileItem.MIN_RESOLUTION, "")) : "",
                image ? String.valueOf(settings.getOrDefault(FileItem.MAX_RESOLUTION, "")) : "");
    }

    /** The limits as constraint options. */
    public Map<String, Object> options() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(FileItem.FILE_EXTENSIONS, String.join(" ", extensions));
        options.put(FileItem.MAX_FILESIZE, maxFilesize);
        options.put(FileItem.IMAGE, image);
        options.put(FileItem.MIN_RESOLUTION, minResolution);
        options.put(FileItem.MAX_RESOLUTION, maxResolution);
        return options;
    }

    /** The limits constraint options carry. */
    public static UploadLimits fromOptions(Map<String, Object> options) {
        return new UploadLimits(
                FileItem.extensions(options.get(FileItem.FILE_EXTENSIONS)),
                FileItem.maxFilesize(options.get(FileItem.MAX_FILESIZE)),
                Boolean.TRUE.equals(options.get(FileItem.IMAGE)),
                String.valueOf(options.getOrDefault(FileItem.MIN_RESOLUTION, "")),
                String.valueOf(options.getOrDefault(FileItem.MAX_RESOLUTION, "")));
    }

    /** The same limits with the size held to the site's largest upload as well. */
    public UploadLimits cappedAt(long siteMaxFilesize) {
        long capped = (maxFilesize > 0) ? Math.min(maxFilesize, siteMaxFilesize) : siteMaxFilesize;
        return new UploadLimits(extensions, capped, image, minResolution, maxResolution);
    }

    public String extensionMessage() {
        return "Only files with these extensions are allowed: " + String.join(" ", extensions) + ".";
    }

    public String sizeMessage() {
        return "The file is larger than " + maxFilesize + " bytes.";
    }

    public static String notAnImageMessage() {
        return "The file is not an image.";
    }

    public String tooSmallMessage() {
        return "The image is smaller than " + minResolution + " pixels.";
    }

    public String tooLargeMessage() {
        return "The image is larger than " + maxResolution + " pixels.";
    }

    /** Why a file of this stored name and size is refused, before its content is looked at. */
    public Optional<String> violation(String storedName, long size) {
        if (!extensions.isEmpty() && !extensions.contains(extension(storedName))) {
            return Optional.of(extensionMessage());
        }
        if (maxFilesize > 0 && size > maxFilesize) {
            return Optional.of(sizeMessage());
        }
        return Optional.empty();
    }

    /** Why an image of this size is refused, or nothing for a field that takes any file. */
    public Optional<String> imageViolation(Supplier<Optional<ImageSize>> imageSize) {
        if (!image) {
            return Optional.empty();
        }
        Optional<ImageSize> size = imageSize.get();
        if (size.isEmpty()) {
            return Optional.of(notAnImageMessage());
        }
        Optional<int[]> min = FileItem.resolution(minResolution);
        if (min.isPresent() && size.get().smallerThan(min.get()[0], min.get()[1])) {
            return Optional.of(tooSmallMessage());
        }
        Optional<int[]> max = FileItem.resolution(maxResolution);
        if (max.isPresent() && size.get().largerThan(max.get()[0], max.get()[1])) {
            return Optional.of(tooLargeMessage());
        }
        return Optional.empty();
    }

    static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot < 0) ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
