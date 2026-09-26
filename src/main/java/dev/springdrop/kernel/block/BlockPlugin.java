package dev.springdrop.kernel.block;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A kind of block: what it is called in the block library, how it builds what it
 * shows, what that depends on, and the settings a placement of it carries. A
 * module contributes one by annotating it with
 * {@code @SpringDropPlugin(type = BlockPlugin.class)}.
 *
 * <p>A block with nothing to show on a page builds nothing, and its placement is
 * left off that page, label and all.
 */
public interface BlockPlugin {

    /**
     * The prefix every settings element is named with, so a block's settings
     * never share a name with the placement's own label, region, or weight.
     */
    String SETTINGS_PREFIX = "settings_";

    String label();

    Optional<Renderable> build(BlockContext context, Map<String, Object> settings);

    /** The contexts and tags what this block builds varies by. */
    default CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY;
    }

    /** The elements a placement's settings are edited with, filled from those settings. */
    default List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of();
    }

    /** The settings a submission of {@link #settingsForm} gives. */
    default Map<String, Object> settingsValues(Map<String, String> submitted) {
        return Map.of();
    }

    /**
     * Errors in a submission's settings that the elements' own rules cannot
     * catch, keyed by the element they belong to.
     */
    default Map<String, String> validateSettings(Map<String, String> submitted) {
        return Map.of();
    }
}
