package dev.springdrop.web;

/** One node on the moderation dashboard, as its newest revision has it. */
public record ModeratedContentRow(
        long id, String title, String url, String type, String state, String author, String updated,
        boolean editable) {
}
