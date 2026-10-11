package dev.springdrop.kernel.log;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** The activity log backend that keeps events in the {@code activity_log} table, where the log viewer reads them. */
@Component
public class DatabaseLog implements LogBackend {

    public static final String ID = "database";

    private static final Table<?> ACTIVITY_LOG = table("activity_log");
    private static final Field<Long> LOG_ID = field("id", SQLDataType.BIGINT);
    private static final Field<String> TYPE = field("type", SQLDataType.VARCHAR);
    private static final Field<Integer> SEVERITY = field("severity", SQLDataType.INTEGER);
    private static final Field<String> MESSAGE = field("message", SQLDataType.CLOB);
    private static final Field<JSONB> VARIABLES = field("variables", SQLDataType.JSONB);
    private static final Field<Long> UID = field("uid", SQLDataType.BIGINT);
    private static final Field<String> LOCATION = field("location", SQLDataType.CLOB);
    private static final Field<String> REFERER = field("referer", SQLDataType.CLOB);
    private static final Field<String> HOSTNAME = field("hostname", SQLDataType.VARCHAR);
    private static final Field<OffsetDateTime> CREATED_AT = field("created_at", SQLDataType.OFFSETDATETIME);

    private static final TypeReference<Map<String, String>> VARIABLES_TYPE = new TypeReference<>() { };

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public DatabaseLog(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void write(LogEntry entry) {
        dsl.insertInto(ACTIVITY_LOG)
                .columns(TYPE, SEVERITY, MESSAGE, VARIABLES, UID, LOCATION, REFERER, HOSTNAME, CREATED_AT)
                .values(entry.type(), entry.severity().level(), entry.message(),
                        JSONB.valueOf(objectMapper.writeValueAsString(entry.variables())), entry.uid(),
                        entry.location(), entry.referer(), entry.hostname(),
                        entry.timestamp().atOffset(ZoneOffset.UTC))
                .execute();
    }

    /** One page of the events the filter lets through, newest first. */
    public List<LogEntry> page(LogFilter filter, int offset, int limit) {
        return dsl.select().from(ACTIVITY_LOG).where(condition(filter))
                .orderBy(LOG_ID.desc())
                .offset(offset).limit(limit)
                .fetch(this::entry);
    }

    public int count(LogFilter filter) {
        return dsl.fetchCount(ACTIVITY_LOG, condition(filter));
    }

    public Optional<LogEntry> find(long id) {
        return dsl.select().from(ACTIVITY_LOG).where(LOG_ID.eq(id)).fetchOptional(this::entry);
    }

    /** The types of the events kept, in name order. */
    public List<String> types() {
        return dsl.selectDistinct(TYPE).from(ACTIVITY_LOG).orderBy(TYPE).fetch(TYPE);
    }

    /** Removes every event kept. */
    public void clear() {
        dsl.deleteFrom(ACTIVITY_LOG).execute();
    }

    private static Condition condition(LogFilter filter) {
        Condition condition = noCondition();
        if (filter.type().isPresent()) {
            condition = condition.and(TYPE.eq(filter.type().get()));
        }
        if (filter.severity().isPresent()) {
            condition = condition.and(SEVERITY.eq(filter.severity().get().level()));
        }
        return condition;
    }

    private LogEntry entry(Record record) {
        return new LogEntry(
                record.get(LOG_ID),
                record.get(TYPE),
                Severity.ofLevel(record.get(SEVERITY.getName(), Integer.class)).orElseThrow(),
                record.get(MESSAGE),
                objectMapper.readValue(record.get(VARIABLES).data(), VARIABLES_TYPE),
                record.get(UID),
                record.get(LOCATION),
                record.get(REFERER),
                record.get(HOSTNAME),
                record.get(CREATED_AT).toInstant());
    }
}
