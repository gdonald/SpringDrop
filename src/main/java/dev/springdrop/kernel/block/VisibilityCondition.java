package dev.springdrop.kernel.block;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.render.CacheMetadata;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A rule deciding whether a placed block shows on a page, such as which paths
 * or roles it shows for. A module contributes one by annotating it with
 * {@code @SpringDropPlugin(type = VisibilityCondition.class)}.
 *
 * <p>The settings form names its elements under the prefix it is handed, so
 * several conditions share one placement form without their names meeting.
 */
public interface VisibilityCondition {

    String label();

    boolean evaluate(Map<String, Object> settings, BlockContext context);

    /** The contexts the decision varies by, which the page carries whether the block shows or not. */
    CacheMetadata cacheability();

    List<FormElement> settingsForm(String prefix, Map<String, Object> settings);

    /**
     * The settings a submission gives, or nothing when it set none, which leaves
     * the condition off the placement.
     */
    Optional<Map<String, Object>> settingsValues(String prefix, Map<String, String> submitted);
}
