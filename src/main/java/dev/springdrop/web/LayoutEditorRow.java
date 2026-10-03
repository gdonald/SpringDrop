package dev.springdrop.web;

/** One block on the layout editor: its id, its title, the block it shows, its region, and its weight. */
public record LayoutEditorRow(String id, String label, String blockLabel, String region, int weight) {
}
