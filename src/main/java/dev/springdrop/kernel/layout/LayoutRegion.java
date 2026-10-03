package dev.springdrop.kernel.layout;

/** One region of a layout: the id blocks are placed under and the label an editor sees. */
public record LayoutRegion(String id, String label) {
}
