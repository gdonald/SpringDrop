package dev.springdrop.kernel.workflow;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The site's workflows, the workflow types they are built on, and whether
 * someone may move something from one state to another. Each transition is
 * guarded by its own permission, which the first account does not need.
 */
@Component
public class WorkflowManager {

    private final ConfigStore configStore;
    private final PluginRegistry registry;

    public WorkflowManager(ConfigStore configStore, PluginRegistry registry) {
        this.configStore = configStore;
        this.registry = registry;
    }

    /** The permission guarding one transition of one workflow. */
    public static String transitionPermission(String workflowId, String transitionId) {
        return "use " + workflowId + " transition " + transitionId;
    }

    /** Every workflow type, listed by label. */
    public List<WorkflowTypeDefinition> types() {
        PluginManager<WorkflowType> types = typePlugins();
        return types.ids().stream()
                .map(id -> new WorkflowTypeDefinition(id, types.get(id).label()))
                .sorted(Comparator.comparing(WorkflowTypeDefinition::label))
                .toList();
    }

    public boolean hasType(String typeId) {
        return typePlugins().has(typeId);
    }

    public WorkflowType type(String typeId) {
        return typePlugins().get(typeId);
    }

    /** A new workflow of a type, starting with the states and transitions the type gives it. */
    public WorkflowConfig create(String id, String label, String typeId) {
        return type(typeId).initial(id, label);
    }

    /**
     * Saves the workflow unless something makes it unusable, and answers with
     * what does. Besides the workflow's own problems, it has to be of a type the
     * site has and keep every state its type requires.
     */
    public List<String> save(WorkflowConfig workflow) {
        List<String> problems = new ArrayList<>(workflow.problems());
        if (!hasType(workflow.type())) {
            problems.add("The workflow type " + workflow.type() + " is not one the site has.");
        } else {
            for (String required : type(workflow.type()).requiredStates()) {
                if (workflow.state(required).isEmpty()) {
                    problems.add("A " + type(workflow.type()).label() + " workflow has to keep the "
                            + required + " state.");
                }
            }
        }
        if (problems.isEmpty()) {
            configStore.save(WorkflowConfig.configName(workflow.id()), workflow);
        }
        return List.copyOf(problems);
    }

    public Optional<WorkflowConfig> find(String id) {
        return Optional.ofNullable(configStore.read(WorkflowConfig.configName(id), WorkflowConfig.class, null));
    }

    public void delete(String id) {
        configStore.delete(WorkflowConfig.configName(id));
    }

    /** Every workflow, listed by label. */
    public List<WorkflowConfig> all() {
        List<WorkflowConfig> workflows = new ArrayList<>();
        for (String name : configStore.listNames(WorkflowConfig.CONFIG_PREFIX)) {
            find(name.substring(WorkflowConfig.CONFIG_PREFIX.length() + 1)).ifPresent(workflows::add);
        }
        return workflows.stream().sorted(Comparator.comparing(WorkflowConfig::label)).toList();
    }

    public boolean mayUse(WorkflowConfig workflow, WorkflowTransition transition, Authentication authentication) {
        String permission = transitionPermission(workflow.id(), transition.id());
        return AccountPrincipals.bypassesChecks(authentication) || authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    /** The transitions out of a state that someone may use. */
    public List<WorkflowTransition> usable(WorkflowConfig workflow, String fromState, Authentication authentication) {
        return workflow.transitionsFrom(fromState).stream()
                .filter(transition -> mayUse(workflow, transition, authentication))
                .toList();
    }

    /**
     * Why someone may not move something between two states, or nothing when
     * they may: there has to be a transition making that move, and they have to
     * hold its permission.
     */
    public Optional<String> refusal(
            WorkflowConfig workflow, String fromState, String toState, Authentication authentication) {
        Optional<WorkflowTransition> transition = workflow.transitionBetween(fromState, toState);
        if (transition.isEmpty()) {
            return Optional.of("There is no transition from " + fromState + " to " + toState + ".");
        }
        return mayUse(workflow, transition.get(), authentication)
                ? Optional.empty()
                : Optional.of("You may not use the " + transition.get().label() + " transition.");
    }

    private PluginManager<WorkflowType> typePlugins() {
        return registry.managerFor(WorkflowType.class);
    }
}
