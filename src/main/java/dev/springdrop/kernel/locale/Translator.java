package dev.springdrop.kernel.locale;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import dev.springdrop.kernel.cache.CacheTagInvalidator;
import dev.springdrop.kernel.language.LanguageManager;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Translates interface strings into the language of the request. Templates
 * call it as {@code ${@translator.t('Read more')}}. The source language is
 * English, so an English request gets the source as it is. A string with no
 * translation is shown in English and recorded, so the translation page lists
 * it. Each language's translations are kept in memory once read, and saving a
 * translation drops them and invalidates the {@code locale} cache tag every
 * page carries.
 */
@Component("translator")
public class Translator {

    /** What joins the singular and plural forms of a plural string, as in Drupal and gettext tools. */
    public static final String PLURAL_DELIMITER = "\u0003";

    /** The cache tag of everything showing translated strings. */
    public static final String CACHE_TAG = "locale";

    private static final Table<?> SOURCE = table("locale_source");
    private static final Table<?> TARGET = table("locale_target");
    private static final Field<Long> ID = field(DSL.name("locale_source", "id"), SQLDataType.BIGINT);
    private static final Field<String> SOURCE_TEXT = field(DSL.name("locale_source", "source"), SQLDataType.CLOB);
    private static final Field<String> CONTEXT = field(DSL.name("locale_source", "context"), SQLDataType.VARCHAR);
    private static final Field<Long> SOURCE_ID = field(DSL.name("locale_target", "source_id"), SQLDataType.BIGINT);
    private static final Field<String> LANGCODE = field(DSL.name("locale_target", "langcode"), SQLDataType.VARCHAR);
    private static final Field<String> TRANSLATION =
            field(DSL.name("locale_target", "translation"), SQLDataType.CLOB);

    // Unqualified names for writing to one table at a time.
    private static final Field<String> NEW_SOURCE = field(DSL.name("source"), SQLDataType.CLOB);
    private static final Field<String> NEW_CONTEXT = field(DSL.name("context"), SQLDataType.VARCHAR);
    private static final Field<Long> NEW_SOURCE_ID = field(DSL.name("source_id"), SQLDataType.BIGINT);
    private static final Field<String> NEW_LANGCODE = field(DSL.name("langcode"), SQLDataType.VARCHAR);
    private static final Field<String> NEW_TRANSLATION = field(DSL.name("translation"), SQLDataType.CLOB);

    private final DSLContext dsl;
    private final CacheTagInvalidator invalidator;
    private final Map<String, Map<String, String>> loaded = new ConcurrentHashMap<>();

    public Translator(DSLContext dsl, CacheTagInvalidator invalidator) {
        this.dsl = dsl;
        this.invalidator = invalidator;
    }

    public String t(String source) {
        return t(source, "");
    }

    public String t(String source, String context) {
        String langcode = LocaleContextHolder.getLocale().toLanguageTag().toLowerCase(Locale.ROOT);
        if (langcode.equals(LanguageManager.ENGLISH)) {
            return source;
        }
        String translation = translations(langcode).get(key(source, context));
        if (translation != null) {
            return translation;
        }
        record(source, context);
        return source;
    }

    /**
     * The singular or plural form for a count, with {@code @count} replaced by
     * it. The first form is for one, and the second for any other count.
     */
    public String plural(long count, String singular, String plural) {
        String[] forms = t(singular + PLURAL_DELIMITER + plural).split(PLURAL_DELIMITER, -1);
        String form = (count == 1 || forms.length < 2) ? forms[0] : forms[1];
        return form.replace("@count", String.valueOf(count));
    }

    /** Records a source string so it can be translated. Recording one already known changes nothing. */
    public long record(String source, String context) {
        dsl.insertInto(SOURCE).columns(NEW_SOURCE, NEW_CONTEXT).values(source, context).onConflictDoNothing()
                .execute();
        return dsl.select(ID).from(SOURCE).where(SOURCE_TEXT.eq(source)).and(CONTEXT.eq(context)).fetchOne(ID);
    }

    /** Stores a translation, or with an empty one, removes it. */
    public void save(String langcode, String source, String context, String translation) {
        long id = record(source, context);
        if (translation.isEmpty()) {
            dsl.deleteFrom(TARGET).where(SOURCE_ID.eq(id)).and(LANGCODE.eq(langcode)).execute();
        } else {
            store(id, langcode, translation);
        }
        changed();
    }

    /** Stores every entry read from a file as a translation into the language, and gives how many. */
    public int importEntries(String langcode, List<PoFile.Entry> entries) {
        entries.forEach(entry -> store(record(entry.source(), entry.context()), langcode, entry.translation()));
        changed();
        return entries.size();
    }

    private void store(long id, String langcode, String translation) {
        dsl.insertInto(TARGET).columns(NEW_SOURCE_ID, NEW_LANGCODE, NEW_TRANSLATION)
                .values(id, langcode, translation)
                .onConflict(NEW_SOURCE_ID, NEW_LANGCODE).doUpdate().set(NEW_TRANSLATION, translation)
                .execute();
    }

    /** One source string with its translation into the language, by the source's id. */
    public Optional<TranslatableString> string(long id, String langcode) {
        return dsl.select(ID, SOURCE_TEXT, CONTEXT, TRANSLATION)
                .from(SOURCE).leftJoin(TARGET).on(SOURCE_ID.eq(ID)).and(LANGCODE.eq(langcode))
                .where(ID.eq(id))
                .fetchOptional(Translator::string);
    }

    /** Removes every translation into the language. */
    public void deleteTranslations(String langcode) {
        dsl.deleteFrom(TARGET).where(LANGCODE.eq(langcode)).execute();
        changed();
    }

    public Optional<String> translation(String langcode, String source, String context) {
        return Optional.ofNullable(translations(langcode).get(key(source, context)));
    }

    /**
     * One page of the source strings with their translations into the
     * language, by source: those whose source or translation contains the
     * search, and with {@code untranslatedOnly}, those without a translation.
     */
    public List<TranslatableString> strings(String langcode, String search, boolean untranslatedOnly, int offset,
            int limit) {
        return dsl.select(ID, SOURCE_TEXT, CONTEXT, TRANSLATION)
                .from(SOURCE).leftJoin(TARGET).on(SOURCE_ID.eq(ID)).and(LANGCODE.eq(langcode))
                .where(matching(search, untranslatedOnly))
                .orderBy(SOURCE_TEXT, CONTEXT)
                .offset(offset).limit(limit)
                .fetch(Translator::string);
    }

    public int count(String langcode, String search, boolean untranslatedOnly) {
        return dsl.fetchCount(dsl.select(ID).from(SOURCE).leftJoin(TARGET).on(SOURCE_ID.eq(ID))
                .and(LANGCODE.eq(langcode)).where(matching(search, untranslatedOnly)));
    }

    /** Every translated string into the language, by source. */
    public List<TranslatableString> translated(String langcode) {
        return dsl.select(ID, SOURCE_TEXT, CONTEXT, TRANSLATION)
                .from(SOURCE).join(TARGET).on(SOURCE_ID.eq(ID)).and(LANGCODE.eq(langcode))
                .orderBy(SOURCE_TEXT, CONTEXT)
                .fetch(Translator::string);
    }

    private static Condition matching(String search, boolean untranslatedOnly) {
        Condition condition = search.isEmpty() ? DSL.noCondition()
                : SOURCE_TEXT.containsIgnoreCase(search).or(TRANSLATION.containsIgnoreCase(search));
        return untranslatedOnly ? condition.and(TRANSLATION.isNull()) : condition;
    }

    private static TranslatableString string(Record record) {
        String translation = record.get(TRANSLATION);
        return new TranslatableString(record.get(ID), record.get(SOURCE_TEXT), record.get(CONTEXT),
                translation == null ? "" : translation);
    }

    private Map<String, String> translations(String langcode) {
        return loaded.computeIfAbsent(langcode, code -> {
            Map<String, String> byKey = new ConcurrentHashMap<>();
            translated(code).forEach(string -> byKey.put(key(string.source(), string.context()),
                    string.translation()));
            return byKey;
        });
    }

    private void changed() {
        loaded.clear();
        invalidator.invalidate(List.of(CACHE_TAG));
    }

    private static String key(String source, String context) {
        return context + "\u0004" + source;
    }
}
