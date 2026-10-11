package dev.springdrop.kernel.lock;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;

/**
 * Named locks kept in the {@code semaphore} table, so one holder at a time runs
 * work that must not overlap, across every process sharing the database. Taking
 * a lock is one insert that fails when another holder has it. A lock lapses at
 * the end of its lease, so a holder that dies without releasing it does not
 * block the work for good.
 */
@Component
public class LockService {

    private static final Table<?> SEMAPHORE = table("semaphore");
    private static final Field<String> NAME = field("name", SQLDataType.VARCHAR);
    private static final Field<String> OWNER = field("owner", SQLDataType.VARCHAR);
    private static final Field<OffsetDateTime> EXPIRES_AT = field("expires_at", SQLDataType.OFFSETDATETIME);

    private final DSLContext dsl;
    private final Clock clock;

    public LockService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    /** Takes the lock for the lease, returning the owner token that releases it, or empty when it is held. */
    public Optional<String> acquire(String name, Duration lease) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        dsl.deleteFrom(SEMAPHORE).where(NAME.eq(name)).and(EXPIRES_AT.le(now)).execute();
        String owner = UUID.randomUUID().toString();
        int taken = dsl.insertInto(SEMAPHORE).columns(NAME, OWNER, EXPIRES_AT)
                .values(name, owner, now.plus(lease))
                .onConflict(NAME).doNothing()
                .execute();
        return taken == 1 ? Optional.of(owner) : Optional.empty();
    }

    /** Releases the lock when the owner still holds it. A lapsed lease taken by someone else is left alone. */
    public void release(String name, String owner) {
        dsl.deleteFrom(SEMAPHORE).where(NAME.eq(name)).and(OWNER.eq(owner)).execute();
    }

    public boolean isHeld(String name) {
        return dsl.fetchExists(SEMAPHORE, NAME.eq(name).and(EXPIRES_AT.gt(OffsetDateTime.now(clock))));
    }
}
