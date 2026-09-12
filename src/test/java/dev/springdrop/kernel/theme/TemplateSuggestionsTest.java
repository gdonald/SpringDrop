package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TemplateSuggestionsTest {

    @Test
    void anEntitySuggestsIdThenBundleThenTypeEachWithAndWithoutTheViewMode() {
        assertThat(TemplateSuggestions.forEntity("node", "article", "teaser", "7"))
                .containsExactly(
                        "node--7--teaser",
                        "node--7",
                        "node--article--teaser",
                        "node--article",
                        "node--teaser",
                        "node");
    }

    @Test
    void anEntityWithNoBundleSuggestsNothingNamingOne() {
        assertThat(TemplateSuggestions.forEntity("user", null, "compact", "3"))
                .containsExactly("user--3--compact", "user--3", "user--compact", "user");
    }

    @Test
    void anEntityWithNoViewModeOrIdSuggestsItsBundleAndItsType() {
        assertThat(TemplateSuggestions.forEntity("node", "page", "  ", ""))
                .containsExactly("node--page", "node");
    }

    @Test
    void aBaseWithQualifiersSuggestsEachShorterPrefixOfThem() {
        assertThat(TemplateSuggestions.of("block", "system", "branding"))
                .containsExactly("block--system--branding", "block--system", "block");
    }

    @Test
    void aBaseWithNoQualifiersSuggestsOnlyItself() {
        assertThat(TemplateSuggestions.of("page")).containsExactly("page");
    }
}
