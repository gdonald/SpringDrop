package dev.springdrop.kernel.theme;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * The themes the site knows about and which template each suggestion resolves to.
 *
 * <p>The active theme is negotiated per request and held per thread, so two
 * requests under different themes do not read each other's.
 *
 * <p>Resolution runs suggestions outermost and the theme chain innermost, so a
 * more specific template wins wherever it lives: a core {@code node--article}
 * beats a theme's {@code node}, and between two templates of the same
 * specificity the active theme beats its parent, which beats the core codebase.
 */
@Component
public class ThemeRegistry {

    private final Map<String, Theme> themes = new LinkedHashMap<>();
    private final ResourceLoader resources;
    private final String prefix;
    private final String suffix;

    private final ThreadLocal<String> requestTheme = new ThreadLocal<>();
    private final String siteDefault;

    public ThemeRegistry(
            ResourceLoader resources,
            List<Theme> registered,
            @Value("${spring.thymeleaf.prefix:classpath:/templates/}") String prefix,
            @Value("${spring.thymeleaf.suffix:.html}") String suffix,
            @Value("${springdrop.theme.default:}") String defaultTheme) {
        this.resources = resources;
        this.prefix = prefix;
        this.suffix = suffix;
        registered.forEach(this::register);
        this.siteDefault = defaultTheme;
        if (!defaultTheme.isBlank() && !themes.containsKey(defaultTheme)) {
            throw new IllegalArgumentException("No theme registered as '" + defaultTheme + "'");
        }
    }

    public final void register(Theme theme) {
        themes.put(theme.name(), theme);
    }

    public Optional<Theme> find(String name) {
        return Optional.ofNullable(themes.get(name));
    }

    public Set<String> names() {
        return Set.copyOf(themes.keySet());
    }

    /**
     * The theme templates resolve against when a caller names none: the one
     * activated for this request, or the site default when none was.
     */
    public Optional<String> active() {
        String forThisRequest = requestTheme.get();
        return Optional.ofNullable(forThisRequest == null ? siteDefault : forThisRequest)
                .filter(name -> !name.isBlank());
    }

    /**
     * Makes {@code name} the active theme for the current request. Each request
     * negotiates its own theme, so this is held per thread rather than shared.
     */
    public void activate(String name) {
        if (!themes.containsKey(name)) {
            throw new IllegalArgumentException("No theme registered as '" + name + "'");
        }
        requestTheme.set(name);
    }

    /** Drops this request's theme, leaving the site default in charge. */
    public void deactivate() {
        requestTheme.remove();
    }

    /**
     * The template roots searched for the active theme, most specific first and
     * ending with the core codebase, which the empty root stands for.
     */
    public List<String> chain() {
        return chainFor(active().orElse(null));
    }

    /** The template roots searched when {@code themeName} is the active theme. */
    public List<String> chainFor(String themeName) {
        Set<String> roots = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        Optional<String> next = Optional.ofNullable(themeName);
        while (next.isPresent() && seen.add(next.get())) {
            Optional<Theme> theme = find(next.get());
            if (theme.isEmpty()) {
                break;
            }
            roots.add(theme.get().templateRoot());
            next = theme.get().parent();
        }
        roots.add("");
        return List.copyOf(roots);
    }

    /**
     * The first template that exists for any suggestion, searched most specific
     * suggestion first and, within a suggestion, active theme first.
     */
    public Optional<String> resolve(String directory, List<String> suggestions) {
        return resolveFor(active().orElse(null), directory, suggestions);
    }

    public Optional<String> resolveFor(String themeName, String directory, List<String> suggestions) {
        List<String> roots = chainFor(themeName);
        for (String suggestion : suggestions) {
            for (String root : roots) {
                String path = path(root, directory, suggestion);
                if (exists(path)) {
                    return Optional.of(path);
                }
            }
        }
        return Optional.empty();
    }

    private static String path(String root, String directory, String suggestion) {
        return (root.isEmpty() ? "" : root + "/") + directory + "/" + suggestion;
    }

    private boolean exists(String path) {
        return resources.getResource(prefix + path + suffix).exists();
    }
}
