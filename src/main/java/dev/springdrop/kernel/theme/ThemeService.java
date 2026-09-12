package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.render.Renderable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * The entry point to the theme layer: given what is being themed and the
 * variables it carries, chooses the template and hands back a {@link Renderable}
 * the render pipeline draws.
 *
 * <p>A themed template is one file holding one piece of output, with no fragment
 * to name, so a theme overrides it by putting a file of the same name under its
 * own root and needs to know nothing about the file it replaces.
 */
@Service
public class ThemeService {

    private final ThemeRegistry registry;
    private final ApplicationEventPublisher events;

    public ThemeService(ThemeRegistry registry, ApplicationEventPublisher events) {
        this.registry = registry;
        this.events = events;
    }

    public Renderable build(String directory, List<String> suggestions, Map<String, Object> variables) {
        String template = registry.resolve(directory, suggestions)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No template in " + directory + " for any of " + suggestions));

        Map<String, Object> prepared = new LinkedHashMap<>(variables);
        String hook = suggestions.getLast();
        events.publishEvent(new ThemePreprocessEvent(hook, template, suggestions, prepared));

        Renderable node = Renderable.of(template, Renderable.WHOLE_TEMPLATE);
        for (Map.Entry<String, Object> variable : prepared.entrySet()) {
            node = node.with(variable.getKey(), variable.getValue());
        }
        return node;
    }

    /** Themes one rendered entity through its entity-type, bundle, and view-mode suggestions. */
    public Renderable entity(
            String directory,
            String entityType,
            String bundle,
            String viewMode,
            String id,
            Map<String, Object> variables) {
        return build(directory, TemplateSuggestions.forEntity(entityType, bundle, viewMode, id), variables);
    }
}
