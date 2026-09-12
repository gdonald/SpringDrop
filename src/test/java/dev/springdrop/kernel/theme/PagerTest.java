package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PagerTest {

    private static Pager overThreePages(int currentPage) {
        return Pager.of(currentPage, 3, number -> "/minutes?page=" + number);
    }

    @Test
    void aPagerNumbersEveryPageFromOne() {
        assertThat(overThreePages(2).pages())
                .extracting(page -> page.number() + " " + page.url())
                .containsExactly(
                        "1 /minutes?page=1", "2 /minutes?page=2", "3 /minutes?page=3");
    }

    @Test
    void onlyThePageBeingReadIsTheCurrentOne() {
        assertThat(overThreePages(2).pages())
                .filteredOn(Pager.PageLink::current)
                .extracting(Pager.PageLink::number)
                .containsExactly(2);
    }

    @Test
    void theFirstPageHasNoPreviousAndTheLastHasNoNext() {
        assertThat(overThreePages(1).previousUrl()).isEmpty();
        assertThat(overThreePages(3).nextUrl()).isEmpty();
    }

    @Test
    void aPageInTheMiddleHasBothNeighbors() {
        assertThat(overThreePages(2).previousUrl()).contains("/minutes?page=1");
        assertThat(overThreePages(2).nextUrl()).contains("/minutes?page=3");
    }

    @Test
    void aListingThatFitsOnOnePageHasNoPagerToDraw() {
        assertThat(Pager.NONE.pages()).isEmpty();
        assertThat(Pager.NONE.previousUrl()).isEmpty();
        assertThat(Pager.NONE.nextUrl()).isEmpty();
    }
}
