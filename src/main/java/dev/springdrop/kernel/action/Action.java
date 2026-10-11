package dev.springdrop.kernel.action;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.form.FormElement;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

/**
 * Something done to one entity, such as publishing a node or blocking an
 * account. An action is a plugin of this type. A simple action needs nothing
 * more than the entity, and the site offers it as it is. A configurable action
 * runs with settings, and the site offers it once an {@link ActionConfig}
 * stores them.
 */
public interface Action {

    String label();

    /** The type of entity the action works on. */
    String entityType();

    /** Whether running the action over a selection asks the person to confirm first. */
    default boolean destructive() {
        return false;
    }

    /** Whether the action needs settings, so it is offered only through a stored {@link ActionConfig}. */
    default boolean configurable() {
        return false;
    }

    /** The controls for a configurable action's settings, filled in with the ones given. */
    default List<FormElement> configurationForm(Map<String, Object> configuration) {
        return List.of();
    }

    /** What is wrong with a configurable action's settings, by setting name. Empty when nothing is. */
    default Map<String, String> validate(Map<String, Object> configuration) {
        return Map.of();
    }

    /** Whether the person may apply the action to the entity. */
    AccessResult access(EntityData entity, Authentication who);

    void execute(EntityData entity, Map<String, Object> configuration, Authentication who);
}
