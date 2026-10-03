package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.entity.EntityData;

/** One comment in its thread, with how many levels down it sits. */
public record CommentThreadItem(EntityData comment, int depth) {
}
