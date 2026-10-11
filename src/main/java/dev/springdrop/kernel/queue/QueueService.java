package dev.springdrop.kernel.queue;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * A reliable queue over the {@code queue} table. Claiming an item leases it, so
 * two claimers never get the same item, and an item whose claimer died is
 * claimable again once the lease runs out. An item leaves the queue only when
 * its claimer deletes it.
 */
@Component
public class QueueService {

    private static final Table<?> QUEUE = table("queue");
    private static final Field<Long> ID = field("id", SQLDataType.BIGINT);
    private static final Field<String> NAME = field("name", SQLDataType.VARCHAR);
    private static final Field<JSONB> DATA = field("data", SQLDataType.JSONB);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", SQLDataType.OFFSETDATETIME);
    private static final Field<OffsetDateTime> AVAILABLE_AT = field("available_at", SQLDataType.OFFSETDATETIME);
    private static final Field<OffsetDateTime> LEASE_EXPIRES_AT =
            field("lease_expires_at", SQLDataType.OFFSETDATETIME);
    private static final Field<Integer> ATTEMPTS = field("attempts", SQLDataType.INTEGER);
    private static final Field<String> LAST_ERROR = field("last_error", SQLDataType.CLOB);
    private static final Field<OffsetDateTime> QUARANTINED_AT = field("quarantined_at", SQLDataType.OFFSETDATETIME);

    // The oldest claimable item, locked so a concurrent claimer skips to the next one.
    private static final String CLAIM = """
            update queue set lease_expires_at = ?::timestamptz
            where id = (
                select id from queue
                where name = ? and quarantined_at is null and available_at <= ?::timestamptz
                  and (lease_expires_at is null or lease_expires_at <= ?::timestamptz)
                order by id
                limit 1
                for update skip locked)
            returning id, name, data::text as data, attempts, last_error""";

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public QueueService(DSLContext dsl, ObjectMapper objectMapper, Clock clock) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Adds an item, its data stored as JSON, and gives its id. */
    public long enqueue(String queue, Object data) {
        OffsetDateTime now = now();
        return dsl.insertInto(QUEUE).columns(NAME, DATA, CREATED_AT, AVAILABLE_AT)
                .values(queue, JSONB.valueOf(objectMapper.writeValueAsString(data)), now, now)
                .returning(ID)
                .fetchOne(ID);
    }

    /** Leases the oldest item ready to be processed, or gives empty when none is. */
    public Optional<QueueItem> claim(String queue, Duration lease) {
        OffsetDateTime now = now();
        return dsl.resultQuery(CLAIM, now.plus(lease), queue, now, now).fetchOptional().map(this::item);
    }

    /** Removes a processed item. */
    public void delete(QueueItem item) {
        dsl.deleteFrom(QUEUE).where(ID.eq(item.id())).execute();
    }

    /** Hands a claimed item back unprocessed, claimable again at once. */
    public void release(QueueItem item) {
        dsl.update(QUEUE).setNull(LEASE_EXPIRES_AT).set(AVAILABLE_AT, now()).where(ID.eq(item.id())).execute();
    }

    /**
     * Records a failed attempt. Below {@code maxAttempts} failures the item waits
     * {@code retryDelay}, doubled for each failure before this one, and is then
     * claimable again. At {@code maxAttempts} it is quarantined. Gives whether it
     * was quarantined.
     */
    public boolean fail(QueueItem item, String error, int maxAttempts, Duration retryDelay) {
        int attempts = item.attempts() + 1;
        OffsetDateTime now = now();
        boolean quarantine = attempts >= maxAttempts;
        dsl.update(QUEUE)
                .set(ATTEMPTS, attempts)
                .set(LAST_ERROR, error)
                .setNull(LEASE_EXPIRES_AT)
                .set(AVAILABLE_AT, now.plus(retryDelay.multipliedBy(1L << (attempts - 1))))
                .set(QUARANTINED_AT, quarantine ? now : null)
                .where(ID.eq(item.id()))
                .execute();
        return quarantine;
    }

    /** How many items wait in the queue or are being processed, leaving out quarantined ones. */
    public int size(String queue) {
        return dsl.fetchCount(QUEUE, NAME.eq(queue).and(QUARANTINED_AT.isNull()));
    }

    /** The items taken out of the queue for failing too often, oldest first. */
    public List<QueueItem> quarantined(String queue) {
        return dsl.select(ID, NAME, DATA.cast(SQLDataType.CLOB).as("data"), ATTEMPTS, LAST_ERROR).from(QUEUE)
                .where(NAME.eq(queue)).and(QUARANTINED_AT.isNotNull())
                .orderBy(ID)
                .fetch(this::item);
    }

    private QueueItem item(Record record) {
        return new QueueItem(
                record.get("id", Long.class),
                record.get("name", String.class),
                objectMapper.readTree(record.get("data", String.class)),
                record.get("attempts", Integer.class),
                record.get("last_error", String.class));
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}
