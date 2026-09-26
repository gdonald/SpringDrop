package dev.springdrop.kernel.block;

import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.render.CacheMetadata;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Decides whether a placement shows on a page. Every condition it carries has
 * to agree, each one read the other way round when it is negated. A condition
 * whose plugin is gone, because the module providing it was turned off, hides
 * the block, since nothing is left to say it may show.
 */
@Component
public class VisibilityConditionManager {

    private final PluginRegistry registry;

    public VisibilityConditionManager(PluginRegistry registry) {
        this.registry = registry;
    }

    /** Every condition, listed by label. */
    public List<ConditionDefinition> definitions() {
        PluginManager<VisibilityCondition> conditions = conditions();
        return conditions.ids().stream()
                .map(id -> new ConditionDefinition(id, conditions.get(id).label()))
                .sorted(Comparator.comparing(ConditionDefinition::label).thenComparing(ConditionDefinition::id))
                .toList();
    }

    public VisibilityCondition condition(String id) {
        return conditions().get(id);
    }

    public boolean passes(List<ConditionConfig> visibility, BlockContext context) {
        PluginManager<VisibilityCondition> conditions = conditions();
        return visibility.stream().allMatch(config -> conditions.has(config.plugin())
                && conditions.get(config.plugin()).evaluate(config.settings(), context) != config.negate());
    }

    /** What the decision over these conditions varies by. */
    public CacheMetadata cacheability(List<ConditionConfig> visibility) {
        PluginManager<VisibilityCondition> conditions = conditions();
        return visibility.stream()
                .filter(config -> conditions.has(config.plugin()))
                .map(config -> conditions.get(config.plugin()).cacheability())
                .reduce(CacheMetadata.EMPTY, CacheMetadata::merge);
    }

    private PluginManager<VisibilityCondition> conditions() {
        return registry.managerFor(VisibilityCondition.class);
    }
}
