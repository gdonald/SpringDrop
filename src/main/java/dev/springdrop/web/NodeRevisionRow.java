package dev.springdrop.web;

/**
 * One revision on a node's history: its id, when and by whom it was saved,
 * what they wrote about it, whether it is the one the site shows, and what the
 * person reading the history may do with it.
 */
public record NodeRevisionRow(
        long revisionId,
        String date,
        String author,
        String log,
        boolean current,
        boolean revertible,
        boolean deletable) {
}
