package dev.springdrop.kernel.field.widget.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EntityReferenceAutocompleteTagsWidgetTest {

    @Test
    void entriesAreSplitAtCommasAndTrimmed() {
        assertThat(EntityReferenceAutocompleteTagsWidget.entries(" Weather ,Hours (4),  ,Gardening"))
                .containsExactly("Weather", "Hours (4)", "Gardening");
    }

    @Test
    void aQuotedEntryKeepsItsCommasAndDoubledQuotes() {
        assertThat(EntityReferenceAutocompleteTagsWidget.entries("\"Hours, opening\", \"The \"\"main\"\" desk\""))
                .containsExactly("Hours, opening", "The \"main\" desk");
    }

    @Test
    void aQuoteAtTheEndOfTheTextClosesTheEntry() {
        assertThat(EntityReferenceAutocompleteTagsWidget.entries("\"Weather\"")).containsExactly("Weather");
    }

    @Test
    void nothingEnteredIsNoEntries() {
        assertThat(EntityReferenceAutocompleteTagsWidget.entries("")).isEmpty();
    }
}
