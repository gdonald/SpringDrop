package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.Link;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.PageRenderer;
import dev.springdrop.kernel.theme.TemplateSuggestions;
import dev.springdrop.kernel.theme.ThemeService;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** The front page, drawn by the active theme around its own content template. */
@Controller
public class HomeController {

    private static final String WELCOME = "A Drupal-style content platform on Spring.";

    private final ConfigStore configStore;
    private final ThemeService themes;
    private final PageRenderer pages;

    public HomeController(ConfigStore configStore, ThemeService themes, PageRenderer pages) {
        this.configStore = configStore;
        this.themes = themes;
        this.pages = pages;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String home() {
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);

        Renderable content = themes.build(
                "content", TemplateSuggestions.of("front-page"), Map.of("welcome", WELCOME));

        PageChrome chrome = PageChrome.of(site.name(), site.name())
                .withSlogan(site.slogan())
                .withPrimaryNavigation(List.of(new Link("Home", "/")))
                .withLocalActions(List.of(new Link("Edit", "/admin")));

        return pages.render(chrome, content).html();
    }
}
