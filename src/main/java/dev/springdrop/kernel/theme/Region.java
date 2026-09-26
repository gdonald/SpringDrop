package dev.springdrop.kernel.theme;

/**
 * One area of a theme's layout that blocks are placed in: the machine name the
 * layout draws it by, and the label the block layout admin shows.
 */
public record Region(String id, String label) {

    /** The region holding the page's main content. */
    public static final String CONTENT = "content";
}
