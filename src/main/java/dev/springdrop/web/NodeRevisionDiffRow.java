package dev.springdrop.web;

import dev.springdrop.kernel.text.TextDiff;
import java.util.List;

/** One part of a node compared across two revisions: its label, and its text word by word. */
public record NodeRevisionDiffRow(String label, boolean changed, List<TextDiff.Part> parts) {
}
