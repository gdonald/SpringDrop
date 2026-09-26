package dev.springdrop.kernel.block.conditions;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockSettings;
import dev.springdrop.kernel.block.VisibilityCondition;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.render.CacheMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shows a block on pages about an entity of one of the bundles chosen, such as
 * the pages of articles and events. {@link EntityBundleConditionDeriver} makes
 * one per entity type that has bundles.
 */
public class EntityBundleCondition implements VisibilityCondition {

    public static final String BUNDLES = "bundles";

    public static final String ROUTE_CONTEXT = "route";

    private final String entityTypeId;
    private final BundleManager bundles;

    public EntityBundleCondition(String entityTypeId, BundleManager bundles) {
        this.entityTypeId = entityTypeId;
        this.bundles = bundles;
    }

    @Override
    public String label() {
        return "Type of " + entityTypeId;
    }

    @Override
    public boolean evaluate(Map<String, Object> settings, BlockContext context) {
        List<String> chosen = BlockSettings.strings(settings, BUNDLES);
        return context.routeEntity()
                .filter(entity -> entity.entityType().equals(entityTypeId))
                .map(entity -> chosen.contains(entity.bundle()))
                .orElse(false);
    }

    @Override
    public CacheMetadata cacheability() {
        return CacheMetadata.EMPTY.withContext(ROUTE_CONTEXT);
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        List<String> chosen = BlockSettings.strings(settings, BUNDLES);
        return bundles.bundles(entityTypeId).stream()
                .map(bundle -> FormElement.of(ElementType.CHECKBOX, prefix + bundle.id())
                        .label(bundle.label())
                        .value(chosen.contains(bundle.id())))
                .toList();
    }

    @Override
    public Optional<Map<String, Object>> settingsValues(String prefix, Map<String, String> submitted) {
        List<String> chosen = bundles.bundles(entityTypeId).stream()
                .map(BundleDefinition::id)
                .filter(bundleId -> submitted.containsKey(prefix + bundleId))
                .toList();
        return chosen.isEmpty() ? Optional.empty() : Optional.of(Map.of(BUNDLES, chosen));
    }
}
