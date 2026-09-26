package dev.springdrop.web;

import java.util.List;

/** One region of a theme as the block layout lists it, with the blocks placed in it. */
public record BlockLayoutRegion(String id, String label, List<BlockLayoutRow> rows) {

    public BlockLayoutRegion {
        rows = List.copyOf(rows);
    }
}
