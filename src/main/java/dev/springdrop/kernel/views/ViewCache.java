package dev.springdrop.kernel.views;

import dev.springdrop.kernel.cache.CacheTags;
import dev.springdrop.kernel.cache.CacheTagsInvalidatedEvent;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Keeps what view displays drew, so the same display shown again with the
 * same values, choices, and page to someone holding the same permissions is
 * not run again.
 *
 * <p>Each drawing carries cache tags: the view's config, cleared when the view
 * changes, and the list of its base entity type and of each type its
 * relationships reach, cleared when any entity of the type is saved or deleted. The
 * drawings carrying a tag are dropped when the tag is invalidated. Who is asking is part of the key through
 * their permissions, since a result the reader may not view is left out.
 */
@Component
public class ViewCache {

    private record Entry(ViewRenderer.Rendered rendered, Set<String> tags) {
    }

    private final ViewRenderer renderer;
    private final PluginRegistry registry;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public ViewCache(ViewRenderer renderer, PluginRegistry registry) {
        this.renderer = renderer;
        this.registry = registry;
    }

    /** The tag of a view's own config. */
    public static String configTag(String viewId) {
        return CacheTags.config(ViewConfig.configName(viewId));
    }

    /** The tag every listing of an entity type carries. */
    public static String listTag(String entityTypeId) {
        return CacheTags.list(entityTypeId);
    }

    /** The display as drawn before for the same key, or drawn now and kept. */
    public ViewRenderer.Rendered render(ViewConfig view, String displayId, List<String> arguments,
            Map<String, String> input, String path, boolean exposedForm, Authentication authentication) {
        String key = String.join("|", view.id(), displayId, String.join("/", arguments),
                new TreeMap<>(input).toString(), path, String.valueOf(exposedForm), permissions(authentication));
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(
                renderer.render(view, displayId, arguments, input, path, exposedForm), tags(view, displayId)));
        return entry.rendered();
    }

    private Set<String> tags(ViewConfig view, String displayId) {
        Set<String> tags = new TreeSet<>(Set.of(configTag(view.id()), listTag(view.baseEntityType())));
        PluginManager<RelationshipHandler> relationships = registry.managerFor(RelationshipHandler.class);
        List<HandlerConfig> configured = view.options(displayId).relationships();
        for (HandlerConfig relationship : configured == null ? List.<HandlerConfig>of() : configured) {
            if (relationships.has(relationship.plugin())) {
                tags.add(listTag(relationships.get(relationship.plugin())
                        .targetType(view.baseEntityType(), relationship)));
            }
        }
        return tags;
    }

    private static String permissions(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return authentication.getName() + ":" + authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Drops every drawing carrying the tag. */
    public void invalidate(String tag) {
        entries.values().removeIf(entry -> entry.tags().contains(tag));
    }

    public int size() {
        return entries.size();
    }

    @EventListener
    void tagsInvalidated(CacheTagsInvalidatedEvent event) {
        event.tags().forEach(this::invalidate);
    }
}
