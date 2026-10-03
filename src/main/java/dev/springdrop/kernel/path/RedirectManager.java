package dev.springdrop.kernel.path;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The site's redirects. A request for a redirect's source is answered with its
 * status and a {@code Location} of its destination, before anything else
 * handles it. Lookups are kept in memory once made, and any change clears what
 * this instance kept.
 */
@Component
public class RedirectManager {

    /** The statuses a redirect answers with. */
    public static final List<Integer> STATUSES = List.of(301, 302, 303, 307, 308);

    public static final int MOVED_PERMANENTLY = 301;

    /** The longest destination a redirect takes. */
    public static final int MAX_DESTINATION_LENGTH = 2048;

    /** What a destination is written as: a path on the site, or an http or https address. */
    public static final String DESTINATION_PATTERN = "(/[^\\s]*|https?://[^\\s/]+[^\\s]*)";

    public static final String DESTINATION_MESSAGE =
            "Write the destination as a path such as /about, or an address such as https://example.com/.";

    public static final String SELF_MESSAGE = "A redirect cannot send a path to itself.";

    public static final String STATUS_MESSAGE = "Choose a redirect status.";

    private static final Table<?> REDIRECTS = DSL.table(DSL.name("redirect"));
    private static final Field<Long> ID = DSL.field(DSL.name("id"), SQLDataType.BIGINT);
    private static final Field<String> SOURCE = DSL.field(DSL.name("source"), SQLDataType.VARCHAR);
    private static final Field<String> DESTINATION = DSL.field(DSL.name("destination"), SQLDataType.VARCHAR);
    private static final Field<Integer> STATUS = DSL.field(DSL.name("status"), SQLDataType.INTEGER);

    private final DSLContext dsl;
    private final Map<String, Optional<Redirect>> bySource = new ConcurrentHashMap<>();

    public RedirectManager(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Why a redirect cannot be saved, or nothing when it can: its source is
     * written like an alias, its destination as {@link #DESTINATION_PATTERN}
     * says, in at most 2048 characters, the two differ, the status is one of
     * {@link #STATUSES}, and no other redirect has the source.
     */
    public Optional<String> refusal(Long id, String source, String destination, int status) {
        if (!source.matches(PathAliasManager.PATTERN) || source.length() > PathAliasManager.MAX_LENGTH) {
            return Optional.of(PathAliasManager.PATTERN_MESSAGE);
        }
        if (!destination.matches(DESTINATION_PATTERN) || destination.length() > MAX_DESTINATION_LENGTH) {
            return Optional.of(DESTINATION_MESSAGE);
        }
        if (destination.equals(source)) {
            return Optional.of(SELF_MESSAGE);
        }
        if (!STATUSES.contains(status)) {
            return Optional.of(STATUS_MESSAGE);
        }
        return find(source).filter(existing -> !Long.valueOf(existing.id()).equals(id))
                .map(existing -> "The path " + source + " already redirects.");
    }

    /**
     * Saves a new redirect when the id is null, or changes the one with the id.
     *
     * @throws IllegalArgumentException when {@link #refusal} gives a reason
     */
    @Transactional
    public void save(Long id, String source, String destination, int status) {
        refusal(id, source, destination, status).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
        if (id == null) {
            dsl.insertInto(REDIRECTS).columns(SOURCE, DESTINATION, STATUS).values(source, destination, status)
                    .execute();
        } else {
            dsl.update(REDIRECTS).set(SOURCE, source).set(DESTINATION, destination).set(STATUS, status)
                    .where(ID.eq(id)).execute();
        }
        forget();
    }

    /**
     * Sends an old path of a page to its new one with a permanent redirect, and
     * points redirects that sent to the old path at the new one, so no request
     * is sent on twice. A redirect from the new path is taken away, since the
     * page answers there now.
     */
    @Transactional
    public void moved(String oldPath, String newPath) {
        dsl.deleteFrom(REDIRECTS).where(SOURCE.eq(newPath)).execute();
        dsl.update(REDIRECTS).set(DESTINATION, newPath).where(DESTINATION.eq(oldPath)).execute();
        dsl.deleteFrom(REDIRECTS).where(SOURCE.eq(oldPath)).execute();
        dsl.insertInto(REDIRECTS).columns(SOURCE, DESTINATION, STATUS).values(oldPath, newPath, MOVED_PERMANENTLY)
                .execute();
        forget();
    }

    /** Takes away a redirect from a path, such as when a page answers there again. */
    public void deleteFrom(String source) {
        dsl.deleteFrom(REDIRECTS).where(SOURCE.eq(source)).execute();
        forget();
    }

    public void delete(long id) {
        dsl.deleteFrom(REDIRECTS).where(ID.eq(id)).execute();
        forget();
    }

    public Optional<Redirect> find(String source) {
        return bySource.computeIfAbsent(source, key -> dsl.select(ID, SOURCE, DESTINATION, STATUS).from(REDIRECTS)
                .where(SOURCE.eq(source))
                .fetchOptional(row -> new Redirect(row.value1(), row.value2(), row.value3(), row.value4())));
    }

    public Optional<Redirect> find(long id) {
        return dsl.select(ID, SOURCE, DESTINATION, STATUS).from(REDIRECTS).where(ID.eq(id))
                .fetchOptional(row -> new Redirect(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    /** Every redirect, by source. */
    public List<Redirect> all() {
        return dsl.select(ID, SOURCE, DESTINATION, STATUS).from(REDIRECTS).orderBy(SOURCE)
                .fetch(row -> new Redirect(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    private void forget() {
        bySource.clear();
    }
}
