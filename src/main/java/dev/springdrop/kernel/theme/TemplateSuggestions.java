package dev.springdrop.kernel.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the ordered list of template names a piece of output may be drawn by,
 * most specific first. The theme layer walks the list and uses the first name
 * some theme or the core codebase has a template for, so adding
 * {@code node--article} changes articles alone and leaves every other node on
 * {@code node}.
 *
 * <p>Names join their parts with {@code --}, matching the file naming a themer
 * sees on disk.
 */
public interface TemplateSuggestions {

    String SEPARATOR = "--";

    /**
     * Suggestions for one rendered entity, ordered id, then bundle, then neither,
     * each with and without the view mode:
     *
     * <pre>
     * node--7--teaser, node--7, node--article--teaser, node--article, node--teaser, node
     * </pre>
     *
     * <p>A blank bundle, view mode, or id drops the names that would name it.
     */
    static List<String> forEntity(String entityType, String bundle, String viewMode, String id) {
        List<String> names = new ArrayList<>();
        for (Optional<String> scope : narrowestFirst(id, bundle)) {
            for (Optional<String> mode : narrowestFirst(viewMode)) {
                names.add(name(entityType, scope, mode));
            }
        }
        return List.copyOf(names);
    }

    /** Suggestions for {@code base} with the given qualifiers, most specific first. */
    static List<String> of(String base, String... qualifiers) {
        List<String> parts = List.of(qualifiers);
        List<String> names = new ArrayList<>();
        for (int depth = parts.size(); depth >= 0; depth--) {
            names.add(join(base, parts.subList(0, depth)));
        }
        return List.copyOf(names);
    }

    /**
     * The given values that are present, narrowest first, followed by the empty
     * choice that names none of them.
     */
    private static List<Optional<String>> narrowestFirst(String... values) {
        List<Optional<String>> choices = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                choices.add(Optional.of(value));
            }
        }
        choices.add(Optional.empty());
        return choices;
    }

    private static String name(String base, Optional<String> scope, Optional<String> mode) {
        List<String> parts = new ArrayList<>();
        scope.ifPresent(parts::add);
        mode.ifPresent(parts::add);
        return join(base, parts);
    }

    private static String join(String base, List<String> parts) {
        StringBuilder name = new StringBuilder(base);
        for (String part : parts) {
            name.append(SEPARATOR).append(part);
        }
        return name.toString();
    }
}
