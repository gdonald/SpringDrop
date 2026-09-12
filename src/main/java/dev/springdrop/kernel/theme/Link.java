package dev.springdrop.kernel.theme;

/** A labelled destination in the page chrome: a nav item, a breadcrumb, an action. */
public record Link(String label, String url) {
}
