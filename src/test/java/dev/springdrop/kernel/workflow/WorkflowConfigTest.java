package dev.springdrop.kernel.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowConfigTest {

    private static final WorkflowConfig EDITORIAL = new WorkflowConfig("editorial", "Editorial", "content_moderation",
            List.of(new WorkflowState("published", "Published", 1),
                    new WorkflowState("draft", "Draft", 0),
                    new WorkflowState("archived", "Archived", 2)),
            List.of(new WorkflowTransition("archive", "Archive", List.of("published"), "archived", 2),
                    new WorkflowTransition("publish", "Publish", List.of("draft", "published"), "published", 1),
                    new WorkflowTransition("restore", "Restore", List.of("archived"), "draft", 3)));

    @Test
    void statesAndTransitionsAreKeptInWeightOrder() {
        assertThat(EDITORIAL.states()).extracting(WorkflowState::id).containsExactly("draft", "published", "archived");
        assertThat(EDITORIAL.transitions()).extracting(WorkflowTransition::id)
                .containsExactly("publish", "archive", "restore");
    }

    @Test
    void theTransitionBetweenTwoStatesIsFoundByWhereItStartsAndEnds() {
        assertThat(EDITORIAL.transitionBetween("draft", "published")).map(WorkflowTransition::id).contains("publish");
        assertThat(EDITORIAL.transitionBetween("draft", "archived")).isEmpty();
    }

    @Test
    void theTransitionsOutOfAStateAreTheOnesStartingThere() {
        assertThat(EDITORIAL.transitionsFrom("published")).extracting(WorkflowTransition::id)
                .containsExactly("publish", "archive");
    }

    @Test
    void aWorkflowWithLabeledStatesAndSoundTransitionsHasNoProblems() {
        assertThat(EDITORIAL.problems()).isEmpty();
    }

    @Test
    void removingAStateTakesTheTransitionsEndingThereAndThoseLeftStartingNowhere() {
        WorkflowConfig withoutArchived = EDITORIAL.withoutState("archived");

        assertThat(withoutArchived.states()).extracting(WorkflowState::id).containsExactly("draft", "published");
        assertThat(withoutArchived.transitions()).extracting(WorkflowTransition::id).containsExactly("publish");
    }

    @Test
    void removingAStateStopsTheTransitionsStartingThereFromStartingThere() {
        WorkflowConfig withoutDraft = EDITORIAL.withoutState("draft");

        assertThat(withoutDraft.transition("publish")).hasValueSatisfying(publish ->
                assertThat(publish.from()).containsExactly("published"));
        assertThat(withoutDraft.transition("restore")).isEmpty();
    }

    @Test
    void addingAStateOrTransitionWithAnIdInUseReplacesIt() {
        WorkflowConfig renamed = EDITORIAL
                .withState(new WorkflowState("draft", "Work in progress", 0))
                .withTransition(new WorkflowTransition("archive", "Retire", List.of("published"), "archived", 2));

        assertThat(renamed.state("draft")).map(WorkflowState::label).contains("Work in progress");
        assertThat(renamed.transition("archive")).map(WorkflowTransition::label).contains("Retire");
        assertThat(renamed.states()).hasSize(3);
    }

    @Test
    void removingATransitionLeavesTheRest() {
        assertThat(EDITORIAL.withoutTransition("archive").transitions()).extracting(WorkflowTransition::id)
                .containsExactly("publish", "restore");
    }

    @Test
    void eachWayAWorkflowCanBeUnusableIsAProblem() {
        WorkflowConfig broken = new WorkflowConfig("broken", "Broken", "content_moderation",
                List.of(new WorkflowState("draft", "", 0), new WorkflowState("published", "Published", 1)),
                List.of(new WorkflowTransition("nameless", "", List.of("draft"), "published", 0),
                        new WorkflowTransition("nowhere", "Nowhere", List.of(), "published", 1),
                        new WorkflowTransition("lost", "Lost", List.of("draft"), "archived", 2),
                        new WorkflowTransition("stray", "Stray", List.of("gone"), "draft", 3),
                        new WorkflowTransition("again", "Again", List.of("draft"), "published", 4)));

        assertThat(broken.problems()).containsExactly(
                "The state draft needs a label.",
                "The transition nameless needs a label.",
                "The transition Nowhere has to start from a state.",
                "The transition Lost ends in a state the workflow does not have.",
                "The transition Stray starts from a state the workflow does not have.",
                "Another transition already moves from draft to published.");
    }

    @Test
    void relabelingTheWorkflowKeepsItsStatesAndTransitions() {
        WorkflowConfig relabeled = EDITORIAL.withLabel("Publishing");

        assertThat(relabeled.label()).isEqualTo("Publishing");
        assertThat(relabeled.states()).isEqualTo(EDITORIAL.states());
    }
}
