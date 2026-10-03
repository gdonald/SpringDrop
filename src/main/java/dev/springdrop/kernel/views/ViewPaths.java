package dev.springdrop.kernel.views;

import dev.springdrop.kernel.config.ConfigChangedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The paths page and feed displays answer at. A display's path is matched a
 * part at a time: a {@code %} part takes any value as a contextual filter's
 * value, and every other part has to be the same. A path with more or fewer
 * parts does not match, so a display at {@code /admin/content} leaves
 * {@code /admin/content/media} to whatever answers there. The list of displays
 * is kept until a view changes.
 */
@Component
public class ViewPaths {

    private final ViewManager views;
    private final AtomicReference<List<ViewConfig>> known = new AtomicReference<>();

    public ViewPaths(ViewManager views) {
        this.views = views;
    }

    /** A display a path leads to, with the contextual filters' values the path holds. */
    public record Match(ViewConfig view, ViewDisplay display, List<String> arguments) {
    }

    /** The page or feed display a path leads to, the one with the most fixed parts when several do. */
    public Optional<Match> match(String path) {
        Match best = null;
        int bestFixed = -1;
        for (ViewConfig view : loaded()) {
            for (ViewDisplay display : view.displays()) {
                if (!display.plugin().equals(ViewDisplay.PAGE) && !display.plugin().equals(ViewDisplay.FEED)) {
                    continue;
                }
                Optional<List<String>> arguments = arguments(display.text(ViewDisplay.PATH), path);
                int fixed = fixedParts(display.text(ViewDisplay.PATH));
                if (arguments.isPresent() && fixed > bestFixed) {
                    best = new Match(view, display, arguments.get());
                    bestFixed = fixed;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The values a path gives a display's path pattern, or nothing when the path does not match it. */
    static Optional<List<String>> arguments(String pattern, String path) {
        if (pattern.isEmpty() || !pattern.startsWith("/")) {
            return Optional.empty();
        }
        String[] wanted = pattern.substring(1).split("/");
        String[] given = path.substring(1).split("/", -1);
        if (given.length != wanted.length) {
            return Optional.empty();
        }
        List<String> values = new ArrayList<>();
        for (int part = 0; part < given.length; part++) {
            if (wanted[part].equals("%")) {
                values.add(given[part]);
            } else if (!wanted[part].equals(given[part])) {
                return Optional.empty();
            }
        }
        return Optional.of(values);
    }

    private static int fixedParts(String pattern) {
        int fixed = 0;
        for (String part : pattern.split("/")) {
            if (!part.isEmpty() && !part.equals("%")) {
                fixed++;
            }
        }
        return fixed;
    }

    /** The address of a display, its path with the {@code %} parts left out. */
    public static String address(ViewDisplay display) {
        String path = display.text(ViewDisplay.PATH).replaceAll("/%", "");
        return path.isEmpty() ? "/" : path;
    }

    private List<ViewConfig> loaded() {
        List<ViewConfig> current = known.get();
        if (current == null) {
            current = views.all();
            known.set(current);
        }
        return current;
    }

    @EventListener
    void viewsChanged(ConfigChangedEvent event) {
        if (event.name().startsWith(ViewConfig.CONFIG_PREFIX + ".")) {
            known.set(null);
        }
    }
}
