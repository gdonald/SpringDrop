package dev.springdrop.kernel.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A view: a listing of one entity type, built from its default display's
 * options, and shown through displays that override some of them. Stored as
 * the config object {@code views.view.<id>}.
 */
public record ViewConfig(String id, String label, String description, String baseEntityType,
        List<ViewDisplay> displays) {

    public static final String CONFIG_PREFIX = "views.view";

    public ViewConfig {
        displays = List.copyOf(displays);
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public Optional<ViewDisplay> display(String displayId) {
        return displays.stream().filter(display -> display.id().equals(displayId)).findFirst();
    }

    public ViewDisplay defaultDisplay() {
        return display(ViewDisplay.DEFAULT).orElseThrow(() -> new IllegalStateException(
                "The view " + id + " has no default display"));
    }

    /** The options a display shows the view with: its overrides over the default display's. */
    public ViewOptions options(String displayId) {
        ViewOptions defaults = defaultDisplay().overrides();
        return display(displayId).map(display -> display.overrides().over(defaults)).orElse(defaults);
    }

    /** The view with the display added, or put in place of the one with its id. */
    public ViewConfig withDisplay(ViewDisplay display) {
        List<ViewDisplay> changed = new ArrayList<>();
        boolean replaced = false;
        for (ViewDisplay existing : displays) {
            if (existing.id().equals(display.id())) {
                changed.add(display);
                replaced = true;
            } else {
                changed.add(existing);
            }
        }
        if (!replaced) {
            changed.add(display);
        }
        return new ViewConfig(id, label, description, baseEntityType, changed);
    }

    public ViewConfig withoutDisplay(String displayId) {
        return new ViewConfig(id, label, description, baseEntityType,
                displays.stream().filter(display -> !display.id().equals(displayId)).toList());
    }
}
