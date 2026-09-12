package dev.springdrop.kernel.theme;

/**
 * One local task tab, such as View or Edit on a piece of content. The active tab
 * is the one the current route belongs to.
 */
public record Tab(String label, String url, boolean active) {
}
