package dev.springdrop.kernel.theme;

import java.util.List;
import java.util.Optional;

/**
 * A theme: a named directory of templates that may override any template the
 * core codebase or a parent theme provides, plus the name of the theme it
 * inherits from.
 *
 * <p>{@code templateRoot} is the path under the template prefix that the theme's
 * files live in, so a theme rooted at {@code themes/vista} overrides the core
 * template {@code content/node} with {@code themes/vista/content/node}.
 *
 * <p>{@code regions} are the areas of the layout blocks are placed in. A theme
 * that declares none uses its parent's.
 */
public record Theme(String name, Optional<String> parent, String templateRoot, List<Region> regions) {

    public static final String DEFAULT_ROOT_PREFIX = "themes/";

    /** The base theme front-end pages render under. */
    public static final String FRONT_END = "front-end";

    /** The theme admin pages render under. */
    public static final String ADMIN = "admin";

    public Theme {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("A theme needs a name");
        }
        regions = List.copyOf(regions);
    }

    public Theme(String name, Optional<String> parent, String templateRoot) {
        this(name, parent, templateRoot, List.of());
    }

    /** A theme rooted at {@code themes/<name>} that inherits from nothing. */
    public static Theme named(String name) {
        return new Theme(name, Optional.empty(), DEFAULT_ROOT_PREFIX + name);
    }

    /** A theme rooted at {@code themes/<name>} that inherits from {@code parent}. */
    public static Theme extending(String name, String parent) {
        return new Theme(name, Optional.of(parent), DEFAULT_ROOT_PREFIX + name);
    }

    public Theme withRegions(List<Region> declared) {
        return new Theme(name, parent, templateRoot, declared);
    }
}
