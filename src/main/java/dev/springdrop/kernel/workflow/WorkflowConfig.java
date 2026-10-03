package dev.springdrop.kernel.workflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A workflow: the states something moves between and the transitions that move
 * it, under a workflow type that gives them meaning. Stored as the config entity
 * {@code workflows.workflow.<id>}.
 */
public record WorkflowConfig(
        String id, String label, String type, List<WorkflowState> states, List<WorkflowTransition> transitions) {

    public static final String CONFIG_PREFIX = "workflows.workflow";

    public WorkflowConfig {
        states = states.stream().sorted(Comparator.comparingInt(WorkflowState::weight)).toList();
        transitions = transitions.stream().sorted(Comparator.comparingInt(WorkflowTransition::weight)).toList();
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public Optional<WorkflowState> state(String stateId) {
        return states.stream().filter(state -> state.id().equals(stateId)).findFirst();
    }

    public Optional<WorkflowTransition> transition(String transitionId) {
        return transitions.stream().filter(transition -> transition.id().equals(transitionId)).findFirst();
    }

    /** The transition moving something from one state to another, which at most one transition does. */
    public Optional<WorkflowTransition> transitionBetween(String fromState, String toState) {
        return transitions.stream().filter(transition -> transition.leads(fromState, toState)).findFirst();
    }

    /** The transitions that start from one state. */
    public List<WorkflowTransition> transitionsFrom(String fromState) {
        return transitions.stream().filter(transition -> transition.from().contains(fromState)).toList();
    }

    public WorkflowConfig withLabel(String newLabel) {
        return new WorkflowConfig(id, newLabel, type, states, transitions);
    }

    /** The same workflow with this state added, or replacing the state of the same id. */
    public WorkflowConfig withState(WorkflowState state) {
        List<WorkflowState> changed = new ArrayList<>(states.stream()
                .filter(existing -> !existing.id().equals(state.id())).toList());
        changed.add(state);
        return new WorkflowConfig(id, label, type, changed, transitions);
    }

    /**
     * The same workflow without this state. Transitions ending in it go too, and
     * the others stop starting from it, leaving out any that then start nowhere.
     */
    public WorkflowConfig withoutState(String stateId) {
        List<WorkflowTransition> kept = new ArrayList<>();
        for (WorkflowTransition transition : transitions) {
            List<String> from = transition.from().stream().filter(state -> !state.equals(stateId)).toList();
            if (!transition.to().equals(stateId) && !from.isEmpty()) {
                kept.add(new WorkflowTransition(transition.id(), transition.label(), from, transition.to(),
                        transition.weight()));
            }
        }
        return new WorkflowConfig(id, label, type,
                states.stream().filter(state -> !state.id().equals(stateId)).toList(), kept);
    }

    /** The same workflow with this transition added, or replacing the transition of the same id. */
    public WorkflowConfig withTransition(WorkflowTransition transition) {
        List<WorkflowTransition> changed = new ArrayList<>(transitions.stream()
                .filter(existing -> !existing.id().equals(transition.id())).toList());
        changed.add(transition);
        return new WorkflowConfig(id, label, type, states, changed);
    }

    public WorkflowConfig withoutTransition(String transitionId) {
        return new WorkflowConfig(id, label, type, states, transitions.stream()
                .filter(transition -> !transition.id().equals(transitionId)).toList());
    }

    /**
     * What makes the workflow unusable: a state or transition without a label,
     * a transition starting or ending in a state the workflow does not have, one
     * starting nowhere, or two transitions making the same move.
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        Set<String> stateIds = new HashSet<>();
        states.forEach(state -> stateIds.add(state.id()));
        Set<String> moves = new HashSet<>();
        for (WorkflowState state : states) {
            if (state.label().isBlank()) {
                problems.add("The state " + state.id() + " needs a label.");
            }
        }
        for (WorkflowTransition transition : transitions) {
            if (transition.label().isBlank()) {
                problems.add("The transition " + transition.id() + " needs a label.");
            }
            if (transition.from().isEmpty()) {
                problems.add("The transition " + transition.label() + " has to start from a state.");
            }
            if (!stateIds.contains(transition.to())) {
                problems.add("The transition " + transition.label() + " ends in a state the workflow does not have.");
            }
            for (String from : transition.from()) {
                if (!stateIds.contains(from)) {
                    problems.add("The transition " + transition.label()
                            + " starts from a state the workflow does not have.");
                } else if (!moves.add(from + ">" + transition.to())) {
                    problems.add("Another transition already moves from " + from + " to " + transition.to() + ".");
                }
            }
        }
        return List.copyOf(problems);
    }
}
