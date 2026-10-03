package dev.springdrop.kernel.filter;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.filter.filters.HtmlCorrectorFilter;
import dev.springdrop.kernel.filter.filters.HtmlEscapeFilter;
import dev.springdrop.kernel.filter.filters.HtmlRestrictorFilter;
import dev.springdrop.kernel.filter.filters.LineBreakFilter;
import dev.springdrop.kernel.filter.filters.UrlFilter;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The site's text formats, who may write in each, and what text in a format
 * becomes on the page. Text runs through the format's filters in weight order,
 * then through the sanitizer, keeping the tags the format's allowed-HTML filter
 * names, or a broad set of content tags for a format without one. Text in a
 * format the site no longer has is shown as the fallback format shows it.
 */
@Component
public class TextFormatManager {

    public static final String PLAIN_TEXT = "plain_text";

    public static final String RESTRICTED_HTML = "restricted_html";

    public static final String BASIC_HTML = "basic_html";

    public static final String FULL_HTML = "full_html";

    /** The filter drawing media embedded in text. */
    public static final String MEDIA_EMBED_FILTER = "media_embed";

    /** The element embedding a media item in text. */
    public static final String MEDIA_EMBED_TAG = "media-embed";

    /** Adding text formats and choosing the filters each runs. */
    public static final String ADMINISTER_FILTERS = "administer filters";

    private final ConfigStore configStore;
    private final PluginRegistry registry;
    private final HtmlSanitizer sanitizer;

    public TextFormatManager(ConfigStore configStore, PluginRegistry registry, HtmlSanitizer sanitizer) {
        this.configStore = configStore;
        this.registry = registry;
        this.sanitizer = sanitizer;
    }

    /** The permission letting someone write in a format. */
    public static String permission(String formatId) {
        return "use text format " + formatId;
    }

    /**
     * Adds the four formats a site starts with, as the application starts, when
     * it has none of them: Plain text, the fallback anyone may use, Restricted
     * HTML, Basic HTML, and Full HTML.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void installDefaults() {
        String basicTags = HtmlRestrictorFilter.RESTRICTED_TAGS + " <span> <img src alt height width>";
        installIfMissing(new TextFormat(PLAIN_TEXT, "Plain text", 10, true, List.of(
                FilterConfig.of(HtmlEscapeFilter.ID, -10),
                new FilterConfig(UrlFilter.ID, 0, Map.of(UrlFilter.LENGTH, UrlFilter.DEFAULT_LENGTH)),
                FilterConfig.of(LineBreakFilter.ID, 10))));
        installIfMissing(new TextFormat(RESTRICTED_HTML, "Restricted HTML", 1, false, List.of(
                new FilterConfig(HtmlRestrictorFilter.ID, -10,
                        Map.of(HtmlRestrictorFilter.ALLOWED_HTML, HtmlRestrictorFilter.RESTRICTED_TAGS)),
                FilterConfig.of(LineBreakFilter.ID, 0),
                new FilterConfig(UrlFilter.ID, 0, Map.of(UrlFilter.LENGTH, UrlFilter.DEFAULT_LENGTH)),
                FilterConfig.of(HtmlCorrectorFilter.ID, 10))));
        installIfMissing(new TextFormat(BASIC_HTML, "Basic HTML", 0, false, List.of(
                new FilterConfig(HtmlRestrictorFilter.ID, -10, Map.of(HtmlRestrictorFilter.ALLOWED_HTML, basicTags)),
                FilterConfig.of(HtmlCorrectorFilter.ID, 10),
                FilterConfig.of(MEDIA_EMBED_FILTER, -100))));
        installIfMissing(new TextFormat(FULL_HTML, "Full HTML", 2, false, List.of(
                FilterConfig.of(HtmlCorrectorFilter.ID, 10),
                FilterConfig.of(MEDIA_EMBED_FILTER, -100))));
    }

    private void installIfMissing(TextFormat format) {
        if (find(format.id()).isEmpty()) {
            save(format);
        }
    }

    public void save(TextFormat format) {
        configStore.save(TextFormat.configName(format.id()), format);
    }

    public Optional<TextFormat> find(String id) {
        return Optional.ofNullable(configStore.read(TextFormat.configName(id), TextFormat.class, null));
    }

    public void delete(String id) {
        configStore.delete(TextFormat.configName(id));
    }

    /** Every format, lightest first and then by label. */
    public List<TextFormat> all() {
        List<TextFormat> formats = new ArrayList<>();
        for (String name : configStore.listNames(TextFormat.CONFIG_PREFIX)) {
            find(name.substring(TextFormat.CONFIG_PREFIX.length() + 1)).ifPresent(formats::add);
        }
        return formats.stream()
                .sorted(Comparator.comparingInt(TextFormat::weight).thenComparing(TextFormat::label))
                .toList();
    }

    /** The format anyone may use. */
    public TextFormat fallback() {
        return all().stream().filter(TextFormat::fallback).findFirst()
                .orElseThrow(() -> new IllegalStateException("The site has no fallback text format"));
    }

    /**
     * Whether someone may write in a format: anyone in the fallback format, and in
     * any other someone holding its permission, someone who administers formats,
     * or the first account.
     */
    public boolean mayUse(TextFormat format, Authentication authentication) {
        if (format.fallback() || AccountPrincipals.bypassesChecks(authentication)) {
            return true;
        }
        return authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(held -> held.equals(permission(format.id())) || held.equals(ADMINISTER_FILTERS));
    }

    /** The formats someone may write in, lightest first. */
    public List<TextFormat> formatsFor(Authentication authentication) {
        return all().stream().filter(format -> mayUse(format, authentication)).toList();
    }

    /** What text written in a format becomes on the page. */
    public String process(String text, String formatId) {
        TextFormat format = find(formatId).orElseGet(this::fallback);
        PluginManager<TextFilter> filters = registry.managerFor(TextFilter.class);
        String processed = text;
        for (FilterConfig filter : format.filters()) {
            if (filters.has(filter.id())) {
                processed = filters.get(filter.id()).process(processed, filter.settings());
            }
        }
        String sanitized = sanitizer.sanitize(processed, allowedHtml(format));
        for (FilterConfig filter : format.filters()) {
            if (filters.has(filter.id())) {
                sanitized = filters.get(filter.id()).afterSanitizing(sanitized, filter.settings());
            }
        }
        return sanitized;
    }

    /**
     * The tags an editor offers for a format: those it keeps, or none for a
     * format that shows markup as text, which is written in a plain textarea.
     */
    public List<String> editorTags(TextFormat format) {
        if (format.filter(HtmlEscapeFilter.ID).isPresent()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>(allowedHtml(format).tagNames());
        if (format.filter(MEDIA_EMBED_FILTER).isPresent()) {
            tags.add(MEDIA_EMBED_TAG);
        }
        return tags;
    }

    /** The tags a format keeps, which an editor for it offers and no more. */
    public AllowedHtml allowedHtml(TextFormat format) {
        return format.filter(HtmlRestrictorFilter.ID)
                .map(filter -> AllowedHtml.parse(String.valueOf(
                        filter.settings().getOrDefault(HtmlRestrictorFilter.ALLOWED_HTML, ""))))
                .orElse(AllowedHtml.BROAD);
    }
}
