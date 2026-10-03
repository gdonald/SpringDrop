package dev.springdrop.kernel.moderation;

import java.util.List;
import java.util.Map;

/**
 * How a content moderation workflow treats content: what each of its states
 * means, and the content types it moderates. Stored as the config object
 * {@code content_moderation.workflow.<workflow>}.
 */
public record ModerationConfig(String workflowId, Map<String, ModerationStateSettings> states, List<String> bundles) {

    public static final String CONFIG_PREFIX = "content_moderation.workflow";

    public ModerationConfig {
        states = Map.copyOf(states);
        bundles = List.copyOf(bundles);
    }

    public static String configName(String workflowId) {
        return CONFIG_PREFIX + "." + workflowId;
    }

    /** What a state means, which for a state with no settings is an unpublished draft. */
    public ModerationStateSettings settings(String state) {
        return states.getOrDefault(state, ModerationStateSettings.UNPUBLISHED);
    }

    public ModerationConfig withStates(Map<String, ModerationStateSettings> newStates) {
        return new ModerationConfig(workflowId, newStates, bundles);
    }

    public ModerationConfig withBundles(List<String> newBundles) {
        return new ModerationConfig(workflowId, states, newBundles);
    }
}
