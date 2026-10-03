package dev.springdrop.kernel.comment;

import java.util.Map;

/**
 * The settings a comment field instance carries, and what each one means.
 *
 * <ul>
 *   <li>{@code default_mode}: {@code threaded}, replies indented under what
 *       they reply to, or {@code flat}, every comment in the order posted.</li>
 *   <li>{@code anonymous}: whether someone not signed in leaves contact
 *       details, {@code 0} none, {@code 1} optionally, {@code 2} required.</li>
 *   <li>{@code preview}: {@code 0} no preview, {@code 1} optional,
 *       {@code 2} required before saving.</li>
 *   <li>{@code depth}: how many levels a thread may reach, {@code 0} for any.</li>
 * </ul>
 */
public record CommentSettings(String defaultMode, int anonymous, int preview, int depth) {

    public static final String DEFAULT_MODE = "default_mode";

    public static final String ANONYMOUS = "anonymous";

    public static final String PREVIEW = "preview";

    public static final String DEPTH = "depth";

    public static final String THREADED = "threaded";

    public static final String FLAT = "flat";

    public static final int CONTACT_NONE = 0;

    public static final int CONTACT_OPTIONAL = 1;

    public static final int CONTACT_REQUIRED = 2;

    public static final int PREVIEW_NONE = 0;

    public static final int PREVIEW_OPTIONAL = 1;

    public static final int PREVIEW_REQUIRED = 2;

    public static final Map<String, Object> DEFAULTS = Map.of(
            DEFAULT_MODE, THREADED, ANONYMOUS, CONTACT_NONE, PREVIEW, PREVIEW_OPTIONAL, DEPTH, 0);

    /** The settings an instance holds, each one it leaves out taken from the defaults. */
    public static CommentSettings of(Map<String, Object> settings) {
        return new CommentSettings(
                String.valueOf(settings.getOrDefault(DEFAULT_MODE, THREADED)),
                number(settings, ANONYMOUS),
                number(settings, PREVIEW),
                number(settings, DEPTH));
    }

    public boolean threaded() {
        return THREADED.equals(defaultMode);
    }

    /** Whether a comment this many levels down may still be replied to. */
    public boolean allowsReplyAt(int level) {
        return threaded() && (depth == 0 || level + 1 < depth);
    }

    private static int number(Map<String, Object> settings, String key) {
        Object value = settings.getOrDefault(key, DEFAULTS.get(key));
        return (value instanceof Number number) ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
