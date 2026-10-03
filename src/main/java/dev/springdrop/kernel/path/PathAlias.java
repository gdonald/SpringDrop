package dev.springdrop.kernel.path;

/** Another path a page answers at: the alias, the page's own path it stands for, and its language. */
public record PathAlias(long id, String source, String alias, String langcode) {
}
