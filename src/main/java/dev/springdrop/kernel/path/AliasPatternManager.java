package dev.springdrop.kernel.path;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.text.TransliterationService;
import dev.springdrop.kernel.token.TokenContext;
import dev.springdrop.kernel.token.TokenReplacer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The alias patterns of bundles, and making an alias from one.
 *
 * <p>Each token in the pattern is replaced with the page's value, made fit for
 * a path: turned to ASCII, lower case, and with every run of other characters
 * made one dash, so {@code Crème Brûlée!} becomes {@code creme-brulee}. A token
 * with no value leaves nothing. Characters an alias cannot hold in the
 * pattern's own text become dashes, and empty segments are dropped. The alias is cut to the longest an alias may
 * be, and when another page has it already, or the site serves that path, a
 * number is added: {@code -1}, {@code -2}, and on.
 */
@Component
public class AliasPatternManager {

    private static final Pattern TOKEN = Pattern.compile("\\[[a-z0-9_]+:[a-z0-9_:.\\-]+]");

    private final ConfigStore configStore;
    private final TokenReplacer tokens;
    private final TransliterationService transliteration;
    private final PathAliasManager aliases;

    public AliasPatternManager(ConfigStore configStore, TokenReplacer tokens,
            TransliterationService transliteration, PathAliasManager aliases) {
        this.configStore = configStore;
        this.tokens = tokens;
        this.transliteration = transliteration;
        this.aliases = aliases;
    }

    public void save(AliasPattern pattern) {
        configStore.save(AliasPattern.configName(pattern.entityType(), pattern.bundle()), pattern);
    }

    public Optional<AliasPattern> find(String entityType, String bundle) {
        return Optional.ofNullable(configStore.read(AliasPattern.configName(entityType, bundle), AliasPattern.class,
                null));
    }

    public void delete(String entityType, String bundle) {
        configStore.delete(AliasPattern.configName(entityType, bundle));
    }

    /** Every pattern, by entity type and bundle. */
    public List<AliasPattern> all() {
        List<AliasPattern> patterns = new ArrayList<>();
        for (String name : configStore.listNames(AliasPattern.CONFIG_PREFIX)) {
            String[] parts = name.substring(AliasPattern.CONFIG_PREFIX.length() + 1).split("\\.", 2);
            find(parts[0], parts[1]).ifPresent(patterns::add);
        }
        return patterns.stream().sorted(Comparator.comparing(AliasPattern::entityType)
                .thenComparing(AliasPattern::bundle)).toList();
    }

    /**
     * The alias the bundle's pattern makes for an entity whose page is at the
     * source path, free for that source, or nothing when the bundle has no
     * pattern or the pattern makes nothing usable.
     */
    public Optional<String> generate(EntityData entity, String source) {
        return find(entity.entityType(), entity.bundle()).flatMap(pattern -> {
            String made = cleaned(pattern.pattern(), Map.of(entity.entityType(), tokenData(entity)));
            return made.isEmpty() ? Optional.empty() : Optional.of(free(made, source, entity.langcode()));
        });
    }

    /** What the entity's tokens read: its title, id, bundle, and fields, by name. */
    static Map<String, Object> tokenData(EntityData entity) {
        Map<String, Object> data = new LinkedHashMap<>(entity.fields());
        data.put("title", entity.label());
        data.put("id", entity.id());
        data.put("type", entity.bundle());
        return data;
    }

    private String cleaned(String pattern, Map<String, Object> data) {
        Matcher token = TOKEN.matcher(pattern);
        StringBuilder made = new StringBuilder();
        while (token.find()) {
            String value = tokens.replace(token.group(), new TokenContext(data, false, true));
            token.appendReplacement(made, Matcher.quoteReplacement(slug(value)));
        }
        token.appendTail(made);
        String path = ("/" + made).replaceAll("[^A-Za-z0-9/_.~-]+", "-").replaceAll("/{2,}", "/")
                .replaceAll("/$", "");
        return (path.length() > PathAliasManager.MAX_LENGTH)
                ? path.substring(0, PathAliasManager.MAX_LENGTH).replaceAll("[/-]+$", "") : path;
    }

    String slug(String value) {
        return transliteration.transliterate(value).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String free(String alias, String source, String langcode) {
        String candidate = alias;
        for (int suffix = 1; aliases.refusal(source, candidate, langcode).isPresent(); suffix++) {
            String ending = "-" + suffix;
            String base = (alias.length() + ending.length() > PathAliasManager.MAX_LENGTH)
                    ? alias.substring(0, PathAliasManager.MAX_LENGTH - ending.length()) : alias;
            candidate = base + ending;
        }
        return candidate;
    }
}
