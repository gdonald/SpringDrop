package dev.springdrop.kernel.file;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.ValidationRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The parts one value of a file or image field holds, the settings that bound
 * what may be uploaded to it, and reading them back off a stored value.
 */
public final class FileItem {

    public static final String TARGET_ID = "target_id";

    public static final String DESCRIPTION = "description";

    public static final String DISPLAY = "display";

    public static final String ALT = "alt";

    public static final String TITLE = "title";

    public static final String WIDTH = "width";

    public static final String HEIGHT = "height";

    public static final int MAX_DESCRIPTION_LENGTH = 255;

    public static final int MAX_IMAGE_TEXT_LENGTH = 512;

    /** The storage setting naming the scheme uploads are kept in. */
    public static final String URI_SCHEME = "uri_scheme";

    /** Space-separated extensions an upload may have. */
    public static final String FILE_EXTENSIONS = "file_extensions";

    /** The largest upload in bytes, or 0 for no limit beyond the site's. */
    public static final String MAX_FILESIZE = "max_filesize";

    public static final String DESCRIPTION_FIELD = "description_field";

    public static final String ALT_FIELD = "alt_field";

    public static final String ALT_FIELD_REQUIRED = "alt_field_required";

    public static final String TITLE_FIELD = "title_field";

    /** The smallest image allowed, written {@code <width>x<height>}, or blank for none. */
    public static final String MIN_RESOLUTION = "min_resolution";

    /** The largest image allowed, written {@code <width>x<height>}, or blank for none. */
    public static final String MAX_RESOLUTION = "max_resolution";

    /** The constraint option marking a field that only takes images. */
    public static final String IMAGE = "image";

    /** Prefixes the Field UI's names for these settings. */
    public static final String SETTINGS_PREFIX = "file_";

    public static final String RESOLUTION_PATTERN = "(\\d{1,5}x\\d{1,5})?";

    private static final String BYTES_PATTERN = "\\d{1,12}";

    private FileItem() {
    }

    /** The managed file one stored value points at. */
    public static Optional<Long> fileId(Object value) {
        return Optional.ofNullable(value)
                .filter(Map.class::isInstance)
                .map(stored -> ((Map<?, ?>) stored).get(TARGET_ID))
                .map(String::valueOf)
                .filter(id -> id.matches("\\d{1,18}"))
                .map(Long::valueOf);
    }

    /** The managed files a field's value points at, whether it holds one value or several. */
    public static List<Long> fileIds(Object fieldValue) {
        List<?> values = (fieldValue instanceof List<?> list) ? list : Arrays.asList(fieldValue);
        List<Long> ids = new ArrayList<>();
        values.forEach(value -> fileId(value).ifPresent(ids::add));
        return ids;
    }

    public static String part(Object value, String key) {
        return Optional.ofNullable(value)
                .filter(Map.class::isInstance)
                .map(stored -> ((Map<?, ?>) stored).get(key))
                .map(String::valueOf)
                .orElse("");
    }

    /** The extensions a setting allows, lower case. */
    public static List<String> extensions(Object setting) {
        return Arrays.stream(String.valueOf(setting == null ? "" : setting).toLowerCase(Locale.ROOT).split("[\\s,]+"))
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .filter(extension -> !extension.isBlank())
                .toList();
    }

    public static long maxFilesize(Object setting) {
        String text = String.valueOf(setting == null ? "0" : setting);
        return text.matches(BYTES_PATTERN) ? Long.parseLong(text) : 0;
    }

    /** A {@code <width>x<height>} setting as its two numbers, or nothing when blank. */
    public static Optional<int[]> resolution(Object setting) {
        String text = String.valueOf(setting == null ? "" : setting);
        if (text.isBlank() || !text.matches(RESOLUTION_PATTERN)) {
            return Optional.empty();
        }
        String[] sides = text.split("x");
        return Optional.of(new int[] {Integer.parseInt(sides[0]), Integer.parseInt(sides[1])});
    }

    /** The Field UI elements every file field's upload limits are edited with. */
    static List<FormElement> uploadLimitsForm(Map<String, Object> settings) {
        return List.of(
                FormElement.of(ElementType.TEXTFIELD, SETTINGS_PREFIX + FILE_EXTENSIONS)
                        .label("Allowed file extensions")
                        .description("Separate extensions with spaces, such as: png jpg pdf")
                        .markRequired()
                        .value(String.join(" ", extensions(settings.get(FILE_EXTENSIONS))))
                        .rule(ValidationRule.pattern("[A-Za-z0-9 .,]+").withMessage("Use letters and digits only.")),
                FormElement.of(ElementType.NUMBER, SETTINGS_PREFIX + MAX_FILESIZE)
                        .label("Maximum upload size in bytes")
                        .description("0 for no limit beyond the site's.")
                        .value(maxFilesize(settings.get(MAX_FILESIZE)))
                        .rule(ValidationRule.pattern(BYTES_PATTERN).withMessage("The size is 0 or more bytes.")));
    }

    /** The upload limits a Field UI submission gives, keeping the fallback extensions when none are valid. */
    static Map<String, Object> uploadLimitsValues(Map<String, String> submitted, String fallbackExtensions) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> extensions = extensions(submitted(submitted, FILE_EXTENSIONS)).stream()
                .filter(extension -> extension.matches("[a-z0-9]+"))
                .toList();
        values.put(FILE_EXTENSIONS, extensions.isEmpty() ? fallbackExtensions : String.join(" ", extensions));
        values.put(MAX_FILESIZE, maxFilesize(submitted(submitted, MAX_FILESIZE)));
        return values;
    }

    static String submitted(Map<String, String> submitted, String setting) {
        return submitted.getOrDefault(SETTINGS_PREFIX + setting, "").trim();
    }

    static boolean checked(Map<String, String> submitted, String setting) {
        return FormRenderer.CHECKED_VALUE.equals(submitted(submitted, setting));
    }

    static FormElement checkbox(String setting, String label, Object current) {
        return FormElement.of(ElementType.CHECKBOX, SETTINGS_PREFIX + setting).label(label)
                .value(Boolean.TRUE.equals(current));
    }
}
