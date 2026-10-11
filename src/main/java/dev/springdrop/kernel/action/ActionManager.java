package dev.springdrop.kernel.action;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.log.ActivityLog;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The actions the site offers, and running one on an entity. Every simple
 * action plugin is offered under its own id, and configurable ones are offered
 * through the {@link ActionConfig}s stored for them. Running an action checks
 * the person may apply it, and logs it on the {@code action} channel.
 */
@Component
public class ActionManager {

    public static final String LOG_CHANNEL = "action";

    private final PluginRegistry plugins;
    private final ConfigStore config;
    private final ActivityLog log;

    public ActionManager(PluginRegistry plugins, ConfigStore config, ActivityLog log) {
        this.plugins = plugins;
        this.config = config;
        this.log = log;
    }

    /** Every action offered, by label. */
    public List<ActionConfig> actions() {
        PluginManager<Action> manager = plugins.managerFor(Action.class);
        Map<String, ActionConfig> offered = new LinkedHashMap<>();
        manager.ids().stream()
                .filter(id -> !manager.get(id).configurable())
                .forEach(id -> offered.put(id, new ActionConfig(id, manager.get(id).label(), id, Map.of())));
        stored().forEach(action -> offered.put(action.id(), action));
        return offered.values().stream()
                .filter(action -> manager.has(action.plugin()))
                .sorted(Comparator.comparing(ActionConfig::label))
                .toList();
    }

    /** The actions offered for one type of entity, by label. */
    public List<ActionConfig> actionsFor(String entityType) {
        return actions().stream().filter(action -> plugin(action).entityType().equals(entityType)).toList();
    }

    public Optional<ActionConfig> find(String id) {
        return actions().stream().filter(action -> action.id().equals(id)).findFirst();
    }

    public Action plugin(ActionConfig action) {
        return plugins.managerFor(Action.class).get(action.plugin());
    }

    /** Stores an action, refusing one whose plugin is unknown or whose settings the plugin rejects. */
    public Map<String, String> save(ActionConfig action) {
        if (!plugins.managerFor(Action.class).has(action.plugin())) {
            return Map.of("plugin", "Choose an action.");
        }
        Map<String, String> errors = plugin(action).validate(action.configuration());
        if (errors.isEmpty()) {
            config.save(ActionConfig.configName(action.id()), action);
        }
        return errors;
    }

    public void delete(String id) {
        config.delete(ActionConfig.configName(id));
    }

    /**
     * Applies the action to the entity as the person, and logs it. A person who
     * may not apply it gets an {@link AccessDeniedException} and nothing changes.
     */
    public void execute(String actionId, EntityData entity, Authentication who) {
        ActionConfig action = find(actionId)
                .orElseThrow(() -> new IllegalArgumentException("No action with id '" + actionId + "'"));
        Action plugin = plugin(action);
        if (!plugin.entityType().equals(entity.entityType())) {
            throw new IllegalArgumentException(action.label() + " does not apply to " + entity.entityType());
        }
        if (!plugin.access(entity, who).allowed()) {
            throw new AccessDeniedException("You may not apply " + action.label() + " to " + entity.label() + ".");
        }
        plugin.execute(entity, action.configuration(), who);
        log.channel(LOG_CHANNEL).notice("{action} was applied to {entity_type} {label} ({id}).", Map.of(
                "action", action.label(),
                "entity_type", entity.entityType(),
                "label", entity.label(),
                "id", String.valueOf(entity.id())));
    }

    private Stream<ActionConfig> stored() {
        return config.listNames(ActionConfig.CONFIG_PREFIX).stream()
                .map(name -> config.read(name, ActionConfig.class, null));
    }
}
