package dev.springdrop.kernel.text;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.text.TextDiff.Change;
import dev.springdrop.kernel.text.TextDiff.Part;
import org.junit.jupiter.api.Test;

class TextDiffTest {

    @Test
    void wordsBothTextsShareAreKeptAndTheRestAreRemovedOrAdded() {
        assertThat(TextDiff.words("Office opens at nine", "Office opens at ten today")).containsExactly(
                new Part(Change.KEPT, "Office opens at"),
                new Part(Change.REMOVED, "nine"),
                new Part(Change.ADDED, "ten today"));
    }

    @Test
    void identicalTextsAreKeptWhole() {
        assertThat(TextDiff.words("Spring schedule", "Spring  schedule"))
                .containsExactly(new Part(Change.KEPT, "Spring schedule"));
    }

    @Test
    void textWrittenFromNothingIsAddedWhole() {
        assertThat(TextDiff.words("", "Spring schedule")).containsExactly(new Part(Change.ADDED, "Spring schedule"));
    }

    @Test
    void textClearedToNothingIsRemovedWhole() {
        assertThat(TextDiff.words("Spring schedule", " ")).containsExactly(new Part(Change.REMOVED, "Spring schedule"));
    }

    @Test
    void aWordReplacedInTheMiddleIsRemovedThenItsReplacementAdded() {
        assertThat(TextDiff.words("open on Monday mornings", "open on Tuesday mornings")).containsExactly(
                new Part(Change.KEPT, "open on"),
                new Part(Change.REMOVED, "Monday"),
                new Part(Change.ADDED, "Tuesday"),
                new Part(Change.KEPT, "mornings"));
    }
}
