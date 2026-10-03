package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.search.SearchExcerptField;
import dev.springdrop.kernel.search.SearchPage;
import dev.springdrop.kernel.search.SearchPageManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.Tab;
import dev.springdrop.kernel.views.ViewCache;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewExecutor;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * Serves the search pages. Each page draws its view: the search form alone
 * until keywords are given, then the form over the results. A tab leads to
 * each search page the reader may use.
 */
@Controller
public class SearchController {

    private final SearchPageManager searchPages;
    private final ViewManager views;
    private final ViewExecutor executor;
    private final ViewCache cache;
    private final ViewRenderer renderer;
    private final BlockPageRenderer pages;
    private final ConfigStore configStore;

    public SearchController(SearchPageManager searchPages, ViewManager views, ViewExecutor executor, ViewCache cache,
            ViewRenderer renderer, BlockPageRenderer pages, ConfigStore configStore) {
        this.searchPages = searchPages;
        this.views = views;
        this.executor = executor;
        this.cache = cache;
        this.renderer = renderer;
        this.pages = pages;
        this.configStore = configStore;
    }

    private Optional<ViewConfig> usableView(SearchPage page, Authentication reader) {
        return views.find(page.viewId()).filter(view -> executor.mayAccess(view, ViewDisplay.DEFAULT, reader));
    }

    private List<SearchPage> usable(Authentication reader) {
        return searchPages.all().stream().filter(page -> usableView(page, reader).isPresent()).toList();
    }

    @GetMapping(SearchPageManager.PATH)
    public String search(Authentication reader) {
        return "redirect:" + usable(reader).stream().findFirst().map(SearchPage::address)
                .orElseThrow(() -> new AccessDeniedException("You may not search this site."));
    }

    @GetMapping(value = SearchPageManager.PATH + "/{path}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String page(@PathVariable String path, @RequestParam Map<String, String> input, Authentication reader) {
        SearchPage page = searchPages.atPath(path).orElseThrow(() -> new EntityNotFoundException("search page",
                path));
        ViewConfig view = usableView(page, reader)
                .orElseThrow(() -> new AccessDeniedException("You may not use this search."));
        String keywords = input.getOrDefault(SearchExcerptField.DEFAULT_IDENTIFIER, "").strip();
        String drawn;
        if (keywords.isEmpty()) {
            String id = ViewRenderer.elementId(view, ViewDisplay.DEFAULT);
            drawn = "<div class=\"view view-" + HtmlUtils.htmlEscape(view.id()) + "\" id=\""
                    + HtmlUtils.htmlEscape(id) + "\">" + renderer.exposedForm(view, ViewDisplay.DEFAULT, input,
                    page.address(), id) + "</div>";
        } else {
            drawn = cache.render(view, ViewDisplay.DEFAULT, List.of(), new LinkedHashMap<>(input), page.address(),
                    true, reader).html();
        }
        List<Tab> tabs = usable(reader).stream()
                .map(each -> new Tab(each.label(), each.address(), each.id().equals(page.id()))).toList();
        SiteInformation site = configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class,
                SiteInformation.DEFAULTS);
        PageChrome chrome = PageChrome.of(site.name(), "Search").withTabs(tabs);
        return pages.render(chrome, Renderable.of("markup").with("value", drawn),
                BlockContext.of(page.address(), "Search")).html();
    }
}
