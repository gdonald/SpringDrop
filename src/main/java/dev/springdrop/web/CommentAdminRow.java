package dev.springdrop.web;

/** One comment on the comment admin: its id, subject, author, what it is about, and when it changed. */
public record CommentAdminRow(long id, String subject, String author, String postedIn, String postedInUrl,
        String updated) {
}
