package dev.springdrop.kernel.user;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.event.EntityEvent;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Personal access tokens: issued to an account under a label, listed and
 * revoked by it, and traded for the account when a request sends one. A token
 * is 32 random bytes, shown once when it is issued. Only its SHA-256 hash is
 * stored. Deleting an account revokes its tokens.
 */
@Component
public class ApiTokenService {

    public static final String PREFIX = "sd_";

    public static final int LABEL_MAX_LENGTH = 128;

    private static final int TOKEN_BYTES = 32;

    private static final Table<Record> API_TOKEN = DSL.table(DSL.name("api_token"));

    private static final Field<Long> ID = DSL.field(DSL.name("id"), SQLDataType.BIGINT);

    private static final Field<Long> ACCOUNT_ID = DSL.field(DSL.name("account_id"), SQLDataType.BIGINT);

    private static final Field<String> LABEL = DSL.field(DSL.name("label"), SQLDataType.VARCHAR);

    private static final Field<String> TOKEN_HASH = DSL.field(DSL.name("token_hash"), SQLDataType.CHAR);

    private static final Field<OffsetDateTime> CREATED = DSL.field(DSL.name("created"),
            SQLDataType.TIMESTAMPWITHTIMEZONE);

    private static final Field<OffsetDateTime> LAST_USED = DSL.field(DSL.name("last_used"),
            SQLDataType.TIMESTAMPWITHTIMEZONE);

    private final DSLContext dsl;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ApiTokenService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    /** A token just issued: what is stored about it, and the token itself, which is not kept. */
    public record IssuedToken(ApiToken token, String secret) {
    }

    public IssuedToken issue(long accountId, String label) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String secret = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        OffsetDateTime now = OffsetDateTime.now(clock);
        long id = dsl.insertInto(API_TOKEN).set(ACCOUNT_ID, accountId).set(LABEL, label.strip())
                .set(TOKEN_HASH, hash(secret)).set(CREATED, now).returning(ID).fetchOne(ID);
        return new IssuedToken(new ApiToken(id, accountId, label.strip(), now, null), secret);
    }

    /** The account's tokens, newest first. */
    public List<ApiToken> tokensOf(long accountId) {
        return dsl.select(ID, ACCOUNT_ID, LABEL, CREATED, LAST_USED).from(API_TOKEN).where(ACCOUNT_ID.eq(accountId))
                .orderBy(CREATED.desc(), ID.desc())
                .fetch(row -> new ApiToken(row.get(ID), row.get(ACCOUNT_ID), row.get(LABEL), row.get(CREATED),
                        row.get(LAST_USED)));
    }

    /** Revokes one of the account's tokens. A token of another account is left alone. */
    public boolean revoke(long accountId, long tokenId) {
        return dsl.deleteFrom(API_TOKEN).where(ID.eq(tokenId)).and(ACCOUNT_ID.eq(accountId)).execute() > 0;
    }

    /** The account a token belongs to, noting that it was used. */
    public Optional<Long> accountFor(String secret) {
        return Optional.ofNullable(dsl.update(API_TOKEN).set(LAST_USED, OffsetDateTime.now(clock))
                .where(TOKEN_HASH.eq(hash(secret))).returning(ACCOUNT_ID).fetchOne(ACCOUNT_ID));
    }

    @EventListener
    void accountDeleted(EntityEvent event) {
        if (event.phase() == EntityEvent.Phase.DELETE && event.entityType().equals(UserEntityType.ID)) {
            dsl.deleteFrom(API_TOKEN).where(ACCOUNT_ID.eq(((Number) ((EntityData) event.entity()).id()).longValue()))
                    .execute();
        }
    }

    /** The token's SHA-256 hash in hex, worked out by the database. */
    private static Field<String> hash(String secret) {
        return DSL.field("encode(sha256(convert_to({0}, 'UTF8')), 'hex')", SQLDataType.CHAR, DSL.val(secret));
    }
}
