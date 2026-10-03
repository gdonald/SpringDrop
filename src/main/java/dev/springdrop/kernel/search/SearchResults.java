package dev.springdrop.kernel.search;

import java.util.List;

/** The hits of one page of a search, best first, and how many documents matched in all. */
public record SearchResults(List<SearchHit> hits, long total) {

    public static final SearchResults NONE = new SearchResults(List.of(), 0);
}
