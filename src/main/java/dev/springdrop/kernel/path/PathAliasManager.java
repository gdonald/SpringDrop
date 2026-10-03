package dev.springdrop.kernel.path;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.routing.RouteRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.transaction.annotation.Transactional;

/**
 * The site's path aliases. A request for an alias is answered as a request for
 * its source, and links the site draws to a source use the alias instead.
 * Lookups both ways are kept in memory once made, and any change to an alias
 * clears what this instance kept.
 */
@Component
public class PathAliasManager {

    /** The longest alias a page takes. */
    public static final int MAX_LENGTH = 255;

    /**
     * What an alias is written as: segments of letters, digits, and {@code _ . ~ -},
     * each after a slash, with no slash at the end.
     */
    public static final String PATTERN = "(/[A-Za-z0-9_.~-]+)+";

    public static final String PATTERN_MESSAGE =
            "Write the alias as /about or /news/2026, with letters, digits, and _ . ~ - between slashes.";

    private static final Table<?> ALIASES = DSL.table(DSL.name("path_alias"));
    private static final Field<Long> ID = DSL.field(DSL.name("id"), SQLDataType.BIGINT);
    private static final Field<String> SOURCE = DSL.field(DSL.name("source"), SQLDataType.VARCHAR);
    private static final Field<String> ALIAS = DSL.field(DSL.name("alias"), SQLDataType.VARCHAR);
    private static final Field<String> LANGCODE = DSL.field(DSL.name("langcode"), SQLDataType.VARCHAR);
    private static final Field<Boolean> GENERATED = DSL.field(DSL.name("generated"), SQLDataType.BOOLEAN);

    /** Paths served as files rather than pages. */
    static final List<String> STATIC_PREFIXES = List.of("/js/", "/assets/", "/css/", "/files/", "/webjars/", "/favicon.ico");

    private final DSLContext dsl;
    private final RedirectManager redirects;
    private final RouteRegistry routes;
    private final ObjectProvider<RequestMappingHandlerMapping> handlers;
    private final Map<String, Optional<String>> sourcesByAlias = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> aliasesBySource = new ConcurrentHashMap<>();

    public PathAliasManager(DSLContext dsl, RedirectManager redirects, RouteRegistry routes,
            @Qualifier("requestMappingHandlerMapping") ObjectProvider<RequestMappingHandlerMapping> handlers) {
        this.dsl = dsl;
        this.redirects = redirects;
        this.routes = routes;
        this.handlers = handlers;
    }

    /**
     * Why an alias cannot be given to a source, or nothing when it can: it has
     * to be written as {@link #PATTERN} says, in at most 255 characters, not be a
     * path the site already serves, so an alias cannot stand in front of a page
     * such as the sign-in form, and not be taken by another source in the
     * language.
     */
    public Optional<String> refusal(String source, String alias, String langcode) {
        if (!alias.matches(PATTERN) || alias.length() > MAX_LENGTH) {
            return Optional.of(PATTERN_MESSAGE);
        }
        if (served(alias)) {
            return Optional.of("The path " + alias + " is already a page of the site.");
        }
        Optional<String> taken = sourceOf(alias, langcode);
        return (taken.isPresent() && !taken.get().equals(source))
                ? Optional.of("The alias " + alias + " is already in use.") : Optional.empty();
    }

    /**
     * Gives a source an alias in a language, in place of the one it had. A
     * blank alias takes the source's alias away. The alias it had is sent on to
     * the new one, or to the source when the alias is taken away, with a
     * permanent redirect, so links to the old path keep working.
     *
     * @throws IllegalArgumentException when {@link #refusal} gives a reason
     */
    @Transactional
    public void save(String source, String alias, String langcode) {
        save(source, alias, langcode, false);
    }

    /** Gives a source an alias, marked as made from a pattern or typed. */
    @Transactional
    public void save(String source, String alias, String langcode, boolean generated) {
        String trimmed = alias.strip();
        Optional<String> previous = aliasOf(source, langcode);
        if (trimmed.isEmpty()) {
            delete(source, langcode);
            previous.ifPresent(old -> redirects.moved(old, source));
            return;
        }
        refusal(source, trimmed, langcode).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
        previous.filter(old -> !old.equals(trimmed)).ifPresent(old -> redirects.moved(old, trimmed));
        redirects.deleteFrom(trimmed);
        dsl.deleteFrom(ALIASES).where(SOURCE.eq(source)).and(LANGCODE.eq(langcode)).execute();
        dsl.insertInto(ALIASES).columns(SOURCE, ALIAS, LANGCODE, GENERATED)
                .values(source, trimmed, langcode, generated).execute();
        forget();
    }

    public void delete(String source, String langcode) {
        dsl.deleteFrom(ALIASES).where(SOURCE.eq(source)).and(LANGCODE.eq(langcode)).execute();
        forget();
    }

    /** Takes away every alias of a source, in every language, such as when its page is deleted. */
    public void deleteAll(String source) {
        dsl.deleteFrom(ALIASES).where(SOURCE.eq(source)).execute();
        forget();
    }

    public Optional<String> sourceOf(String alias, String langcode) {
        return sourcesByAlias.computeIfAbsent(langcode + " " + alias, key -> dsl.select(SOURCE).from(ALIASES)
                .where(ALIAS.eq(alias)).and(LANGCODE.eq(langcode)).fetchOptional(SOURCE));
    }

    public Optional<String> aliasOf(String source, String langcode) {
        return aliasesBySource.computeIfAbsent(langcode + " " + source, key -> dsl.select(ALIAS).from(ALIASES)
                .where(SOURCE.eq(source)).and(LANGCODE.eq(langcode)).fetchOptional(ALIAS));
    }

    /** Whether the source's alias in the language was made from a pattern. */
    public boolean generated(String source, String langcode) {
        return Boolean.TRUE.equals(dsl.select(GENERATED).from(ALIASES).where(SOURCE.eq(source))
                .and(LANGCODE.eq(langcode)).fetchOne(GENERATED));
    }

    /** The path a link to a page uses: the page's alias in the default language, or its own path. */
    public String outbound(String path) {
        return aliasOf(path, EntityData.DEFAULT_LANGCODE).orElse(path);
    }

    /** Every alias, by alias. */
    public List<PathAlias> all() {
        return dsl.select(ID, SOURCE, ALIAS, LANGCODE).from(ALIASES).orderBy(ALIAS, LANGCODE)
                .fetch(row -> new PathAlias(row.value1(), row.value2(), row.value3(), row.value4()));
    }

    /** Whether the site serves the path itself: a controller, a registered route, or a static file. */
    public boolean served(String path) {
        if (STATIC_PREFIXES.stream().anyMatch(path::startsWith)
                || routes.match(path).isPresent()) {
            return true;
        }
        PathContainer container = PathContainer.parsePath(path);
        return handlers.getObject().getHandlerMethods().keySet().stream()
                .map(RequestMappingInfo::getPathPatternsCondition)
                .filter(Objects::nonNull)
                .flatMap(condition -> condition.getPatterns().stream())
                .anyMatch(pattern -> pattern.matches(container));
    }

    private void forget() {
        sourcesByAlias.clear();
        aliasesBySource.clear();
    }
}
