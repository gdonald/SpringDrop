package dev.springdrop.kernel.views;

/**
 * How a view lays its results out: a table, a list, a grid, or one after
 * another. A style is a plugin registered with
 * {@code @SpringDropPlugin(type = StylePlugin.class)}.
 */
public interface StylePlugin extends ViewPlugin {

    /** The markup of all the results. */
    String render(StyleContext context);
}
