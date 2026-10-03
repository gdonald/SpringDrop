package dev.springdrop.kernel.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SearchKeywordsTest {

    @Test
    void keywordsAreSplitIntoLowerCaseWordsOfLettersAndDigits() {
        assertThat(SearchKeywords.words("  Bridge, canal & Brücke!! bridge 2026 ")).containsExactly("bridge",
                "canal", "brücke", "2026");
    }

    @Test
    void punctuationAloneHasNoWords() {
        assertThat(SearchKeywords.words("&|!:*()'")).isEmpty();
    }

    @Test
    void atMostThirtyTwoWordsAreKept() {
        String many = IntStream.range(0, 40).mapToObj(number -> "w" + number).collect(Collectors.joining(" "));

        assertThat(SearchKeywords.words(many)).hasSize(SearchKeywords.MAX_WORDS);
    }
}
