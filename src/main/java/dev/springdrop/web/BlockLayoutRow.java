package dev.springdrop.web;

/**
 * One placed block as the block layout lists it: the placement, what it is
 * labeled, which block it draws, and where it sits.
 */
public record BlockLayoutRow(String id, String label, String blockLabel, String region, int weight) {
}
