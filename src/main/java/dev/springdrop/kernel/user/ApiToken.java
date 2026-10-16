package dev.springdrop.kernel.user;

import java.time.OffsetDateTime;

/** A personal access token as its account sees it: never the token itself, which is shown once. */
public record ApiToken(long id, long accountId, String label, OffsetDateTime created, OffsetDateTime lastUsed) {
}
