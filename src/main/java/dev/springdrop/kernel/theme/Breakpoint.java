package dev.springdrop.kernel.theme;

import java.util.List;

/**
 * A width range a theme lays pages out for: a media query, a weight ordering
 * it among its group's breakpoints from narrowest to widest, and the pixel
 * densities it is drawn at, such as {@code 1x} and {@code 2x}.
 */
public record Breakpoint(String id, String label, String mediaQuery, int weight, List<String> multipliers) {

    /** The query matching every width. */
    public static final String ALL = "all";

    public Breakpoint {
        multipliers = List.copyOf(multipliers);
    }
}
