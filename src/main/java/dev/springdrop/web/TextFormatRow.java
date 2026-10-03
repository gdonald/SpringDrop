package dev.springdrop.web;

/** One text format on the formats admin: its id, label, the roles that may use it, and whether it is the fallback. */
public record TextFormatRow(String id, String label, String roles, boolean fallback) {
}
