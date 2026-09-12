package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.Link;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.PageRenderer;
import dev.springdrop.kernel.theme.TemplateSuggestions;
import dev.springdrop.kernel.theme.ThemeService;
import dev.springdrop.kernel.token.TokenDefinition;
import dev.springdrop.kernel.token.TokenProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Lists every token the registered providers offer, so an editor filling in a
 * mail template or a pattern can see what is available and copy the exact token.
 */
@Controller
public class TokenBrowserController {

    public static final String PATH = "/admin/help/tokens";

    private static final String TITLE = "Available tokens";
    private static final String DESCRIPTION =
            "Paste a token into any field that accepts one. Tokens are replaced when the text is rendered.";

    /** One provider's tokens, grouped for the browser table. */
    public record TokenGroup(String type, List<TokenDefinition> tokens) {
    }

    private final List<TokenProvider> providers;
    private final ConfigStore configStore;
    private final ThemeService themes;
    private final PageRenderer pages;

    public TokenBrowserController(
            List<TokenProvider> providers,
            ConfigStore configStore,
            ThemeService themes,
            PageRenderer pages) {
        this.providers = providers;
        this.configStore = configStore;
        this.themes = themes;
        this.pages = pages;
    }

    @GetMapping(value = PATH, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String browse() {
        List<TokenGroup> groups = providers.stream()
                .map(provider -> new TokenGroup(provider.type(), provider.availableTokens()))
                .sorted(Comparator.comparing((TokenGroup group) -> group.type()))
                .toList();

        Renderable content = themes.build(
                "content",
                TemplateSuggestions.of("token-browser"),
                Map.of("tokenGroups", groups, "description", DESCRIPTION));

        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);

        PageChrome chrome = PageChrome.of(site.name(), TITLE)
                .withBreadcrumbs(List.of(new Link("Home", "/"), new Link("Administration", "/admin")));

        return pages.render(chrome, content).html();
    }
}
