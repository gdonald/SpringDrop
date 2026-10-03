package dev.springdrop.kernel.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlTrimmerTest {

    @Test
    void htmlShorterThanTheLengthIsLeftWhole() {
        assertThat(HtmlTrimmer.trim("<p>Open <em>daily</em></p>", 40)).isEqualTo("<p>Open <em>daily</em></p>");
    }

    @Test
    void textIsCutAtTheLastWholeWordAndWhatFollowsDropped() {
        assertThat(HtmlTrimmer.trim("<p>Opening hours change</p><p>Second paragraph</p>", 16))
                .isEqualTo("<p>Opening hours...</p>");
    }

    @Test
    void aSingleWordLongerThanTheLengthIsCutWhereTheLengthEnds() {
        assertThat(HtmlTrimmer.trim("<p>Supercalifragilistic</p>", 5)).isEqualTo("<p>Super...</p>");
    }

    @Test
    void theCutClosesEveryTagLeftOpen() {
        assertThat(HtmlTrimmer.trim("<ul><li><strong>First item</strong> goes on</li><li>Second</li></ul>", 8))
                .isEqualTo("<ul><li><strong>First...</strong></li></ul>");
    }
}
