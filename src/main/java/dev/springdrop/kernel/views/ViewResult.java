package dev.springdrop.kernel.views;

import java.util.List;

/**
 * What a view found for one display: the results on the page asked for, how
 * many there are in all, the page shown, how many pages there are, and the
 * options the display was run with.
 */
public record ViewResult(List<ResultRow> rows, long total, int page, int totalPages, ViewOptions options) {

    public ViewResult {
        rows = List.copyOf(rows);
    }
}
