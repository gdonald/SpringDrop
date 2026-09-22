package dev.springdrop.kernel.theme;

import java.util.List;

/**
 * A labelled destination in the page chrome: a nav item, a breadcrumb, an
 * action. A nav item may carry the items below it, and may be marked as lying on
 * the trail to the page being shown, so a theme can open that branch and mark it
 * current. Links that are not navigation carry neither.
 */
public record Link(String label, String url, List<Link> children, boolean active) {

    public Link {
        children = List.copyOf(children);
    }

    public Link(String label, String url) {
        this(label, url, List.of(), false);
    }
}
