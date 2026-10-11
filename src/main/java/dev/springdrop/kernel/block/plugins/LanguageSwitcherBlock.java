package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.cache.CacheContexts;
import dev.springdrop.kernel.cache.CacheTags;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageLinks;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.language.NegotiationSettings;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/**
 * Links to the page being read in each of the site's languages, the current
 * one marked active. A site with one language, or with no negotiation method
 * a link can choose a language through, shows nothing.
 */
@SpringDropPlugin(id = LanguageSwitcherBlock.ID, type = BlockPlugin.class)
public class LanguageSwitcherBlock implements BlockPlugin {

    public static final String ID = "language_block";

    private final LanguageManager languages;
    private final LanguageLinks links;

    public LanguageSwitcherBlock(LanguageManager languages, LanguageLinks links) {
        this.languages = languages;
        this.links = links;
    }

    @Override
    public String label() {
        return "Language switcher";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        List<LanguageConfig> all = languages.all();
        if (all.size() < 2 || links.url(all.getFirst(), context.path()).isEmpty()) {
            return Optional.empty();
        }
        String current = languages.current().id();
        StringBuilder markup = new StringBuilder("<ul class=\"nav language-switcher\">");
        for (LanguageConfig language : all) {
            boolean active = language.id().equals(current);
            markup.append("<li class=\"nav-item\"><a class=\"nav-link").append(active ? " active" : "")
                    .append("\" href=\"").append(HtmlUtils.htmlEscape(links.url(language, context.path()).get()))
                    .append("\" hreflang=\"").append(HtmlUtils.htmlEscape(language.id())).append("\"")
                    .append(active ? " aria-current=\"true\"" : "").append(">")
                    .append(HtmlUtils.htmlEscape(language.label())).append("</a></li>");
        }
        return Optional.of(Renderable.of("markup").with("value", markup.append("</ul>").toString()));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withContext(CacheContexts.LANGUAGES).withContext(CacheContexts.URL_PATH)
                .withTag(CacheTags.config(NegotiationSettings.CONFIG_NAME))
                .withTag(CacheTags.configList(LanguageConfig.CONFIG_PREFIX));
    }
}
