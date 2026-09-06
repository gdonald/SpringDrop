package dev.springdrop.kernel.render;

/**
 * Builds the part of a page that could not be cached with the rest of it. The
 * shell renders a placeholder in its place, and the builder runs afterwards.
 */
@FunctionalInterface
public interface LazyBuilder {

    Renderable build();
}
