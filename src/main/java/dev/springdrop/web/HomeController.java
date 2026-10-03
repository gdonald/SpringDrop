package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.menu.BreadcrumbBuilder;
import dev.springdrop.kernel.menu.MenuNavigation;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.Link;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.TemplateSuggestions;
import dev.springdrop.kernel.theme.ThemeService;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewCache;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewEmbed;
import dev.springdrop.kernel.views.ViewRenderer;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * The site's home, and the listing of front page content, drawn by the
 * {@code frontpage} view: published nodes promoted to the front page, sticky
 * ones first and then the newest, a page at a time. The home shows the listing
 * unless the site names another path as its front page, in which case it shows
 * that path's page.
 */
@Controller
public class HomeController {

    private static final String WELCOME = "A Drupal-style content platform on Spring.";

    public static final String PATH = "/";

    public static final String LISTING_PATH = SiteInformation.DEFAULT_FRONT_PAGE;

    public static final String PAGE = "page";

    private final ConfigStore configStore;
    private final ThemeService themes;
    private final BlockPageRenderer pages;
    private final MenuNavigation navigation;
    private final BreadcrumbBuilder breadcrumbs;
    private final ViewEmbed views;

    public HomeController(
            ConfigStore configStore,
            ThemeService themes,
            BlockPageRenderer pages,
            MenuNavigation navigation,
            BreadcrumbBuilder breadcrumbs,
            ViewEmbed views) {
        this.configStore = configStore;
        this.themes = themes;
        this.pages = pages;
        this.navigation = navigation;
        this.breadcrumbs = breadcrumbs;
        this.views = views;
    }

    @GetMapping(PATH)
    public void home(@RequestParam(name = PAGE, defaultValue = "1") String page,
            HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        SiteInformation site = site();
        if (!site.frontPage().equals(LISTING_PATH)) {
            request.getRequestDispatcher(site.frontPage()).forward(request, response);
            return;
        }
        response.setContentType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8");
        response.getWriter().write(listing(site, PATH, page));
    }

    @GetMapping(value = LISTING_PATH, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String frontPageContent(@RequestParam(name = PAGE, defaultValue = "1") String page) {
        return listing(site(), LISTING_PATH, page);
    }

    /** One page of front page content, drawn by the front page view, under a welcome when there is none. */
    private String listing(SiteInformation site, String path, String requestedPage) {
        Optional<ViewRenderer.Rendered> listed = views.render(DefaultViews.FRONTPAGE, ViewDisplay.DEFAULT, List.of(),
                Map.of(ViewRenderer.PAGE, requestedPage), path, false);
        boolean empty = listed.map(rendered -> rendered.result().rows().isEmpty()).orElse(true);

        Renderable content = themes.build("content", TemplateSuggestions.of("front-page"),
                Map.of("welcome", WELCOME, "empty", empty));
        if (!empty) {
            content = content.child(Renderable.of("markup").with("value", listed.get().html()));
        }
        content = content.cacheTag(NodeService.LIST_CACHE_TAG).cacheTag(ViewCache.configTag(DefaultViews.FRONTPAGE));

        PageChrome chrome = PageChrome.of(site.name(), site.name())
                .withSlogan(site.slogan())
                .withPrimaryNavigation(navigation.primary(path))
                .withBreadcrumbs(breadcrumbs.build(path))
                .withLocalActions(List.of(new Link("Edit", "/admin")))
                .withPageCache();
        return pages.render(chrome, content, BlockContext.of(path, site.name())).html();
    }

    private SiteInformation site() {
        return configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
    }
}
