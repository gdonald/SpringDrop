package dev.springdrop.kernel.config;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.SQLDataType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Typed configuration object store. Config objects are stored as {@code jsonb}
 * keyed by name and deserialized into the caller's type, which serves as the
 * schema. Environment overrides are layered on read without mutating the stored
 * value, and a {@link ConfigChangedEvent} is published on every save.
 */
@Component
public class ConfigStore {

    private static final Table<?> CONFIG = table("config");
    private static final Field<String> NAME = field("name", SQLDataType.VARCHAR);
    private static final Field<JSONB> DATA = field("data", SQLDataType.JSONB);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final List<ConfigOverrideProvider> overrideProviders;

    public ConfigStore(
            DSLContext dsl,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events,
            List<ConfigOverrideProvider> overrideProviders) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.events = events;
        this.overrideProviders = overrideProviders;
    }

    public <T> T read(String name, Class<T> type, T defaults) {
        JSONB stored = dsl.select(DATA).from(CONFIG).where(NAME.eq(name)).fetchOne(DATA);
        T base = (stored == null) ? defaults : objectMapper.readValue(stored.data(), type);

        Map<String, Object> overrides = new LinkedHashMap<>();
        for (ConfigOverrideProvider provider : overrideProviders) {
            provider.overrides(name).ifPresent(overrides::putAll);
        }
        if (overrides.isEmpty()) {
            return base;
        }

        ObjectNode merged = objectMapper.valueToTree(base);
        merged.setAll(objectMapper.<ObjectNode>valueToTree(overrides));
        return objectMapper.convertValue(merged, type);
    }

    public void save(String name, Object value) {
        JSONB json = JSONB.valueOf(objectMapper.writeValueAsString(value));
        dsl.insertInto(CONFIG).columns(NAME, DATA).values(name, json)
                .onConflict(NAME).doUpdate().set(DATA, json)
                .execute();
        events.publishEvent(new ConfigChangedEvent(name));
    }
}
