package dev.springdrop.kernel.state;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
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
 * Key/value store for non-config runtime state, kept separate from the config
 * store so it is excluded from configuration export. Values are stored as
 * {@code jsonb}. An expiring variant lapses values after a TTL.
 */
@Component
public class StateService {

    private static final Table<?> KEY_VALUE = table("key_value");
    private static final Table<?> KEY_VALUE_EXPIRE = table("key_value_expire");
    private static final Field<String> COLLECTION = field("collection", SQLDataType.VARCHAR);
    private static final Field<String> KEY = field("key", SQLDataType.VARCHAR);
    private static final Field<JSONB> VALUE = field("value", SQLDataType.JSONB);
    private static final Field<OffsetDateTime> EXPIRES_AT = field("expires_at", SQLDataType.OFFSETDATETIME);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StateService(DSLContext dsl, ObjectMapper objectMapper, Clock clock) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public <T> Optional<T> get(String collection, String key, Class<T> type) {
        JSONB value = dsl.select(VALUE).from(KEY_VALUE)
                .where(COLLECTION.eq(collection)).and(KEY.eq(key))
                .fetchOne(VALUE);
        return Optional.ofNullable(value).map(json -> objectMapper.readValue(json.data(), type));
    }

    public void set(String collection, String key, Object value) {
        JSONB json = JSONB.valueOf(objectMapper.writeValueAsString(value));
        dsl.insertInto(KEY_VALUE).columns(COLLECTION, KEY, VALUE).values(collection, key, json)
                .onConflict(COLLECTION, KEY).doUpdate().set(VALUE, json)
                .execute();
    }

    public void remove(String collection, String key) {
        dsl.deleteFrom(KEY_VALUE)
                .where(COLLECTION.eq(collection)).and(KEY.eq(key))
                .execute();
    }

    public void setWithExpiry(String collection, String key, Object value, Duration ttl) {
        JSONB json = JSONB.valueOf(objectMapper.writeValueAsString(value));
        OffsetDateTime expiresAt = OffsetDateTime.now(clock).plus(ttl);
        dsl.insertInto(KEY_VALUE_EXPIRE).columns(COLLECTION, KEY, VALUE, EXPIRES_AT)
                .values(collection, key, json, expiresAt)
                .onConflict(COLLECTION, KEY).doUpdate().set(VALUE, json).set(EXPIRES_AT, expiresAt)
                .execute();
    }

    public <T> Optional<T> getExpiring(String collection, String key, Class<T> type) {
        Record record = dsl.select(VALUE, EXPIRES_AT).from(KEY_VALUE_EXPIRE)
                .where(COLLECTION.eq(collection)).and(KEY.eq(key))
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        if (record.get(EXPIRES_AT).isBefore(OffsetDateTime.now(clock))) {
            return Optional.empty();
        }
        return Optional.of(objectMapper.readValue(record.get(VALUE).data(), type));
    }
}
