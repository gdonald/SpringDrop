package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.ViewCache;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewExecutor;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewPageFilter;
import dev.springdrop.kernel.views.ViewPaths;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.web.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * Serves page and feed displays of views. A request reaches here from the
 * display's own path, through {@link ViewPageFilter}, or at
 * {@code /views/page/<view>/<display>}. A page is drawn in the theme's page; a
 * feed is RSS, an item per result with its title, link, and date.
 */
@Controller
public class ViewPageController {

    private final ViewManager views;
    private final ViewExecutor executor;
    private final ViewCache cache;
    private final BlockPageRenderer pages;
    private final ConfigStore configStore;
    private final EntityTypeManager entityTypes;
    private final PathAliasManager aliases;

    public ViewPageController(ViewManager views, ViewExecutor executor, ViewCache cache, BlockPageRenderer pages,
            ConfigStore configStore, EntityTypeManager entityTypes, PathAliasManager aliases) {
        this.views = views;
        this.executor = executor;
        this.cache = cache;
        this.pages = pages;
        this.configStore = configStore;
        this.entityTypes = entityTypes;
        this.aliases = aliases;
    }

    @GetMapping(ViewPageFilter.PAGE_PREFIX + "{view}/{display}")
    public ResponseEntity<String> page(@PathVariable String view, @PathVariable String display,
            @RequestParam Map<String, String> input, HttpServletRequest request, Authentication authentication) {
        ViewConfig config = views.find(view).orElseThrow(() -> new EntityNotFoundException("view", view));
        ViewDisplay shown = config.display(display)
                .filter(each -> each.plugin().equals(ViewDisplay.PAGE) || each.plugin().equals(ViewDisplay.FEED))
                .orElseThrow(() -> new EntityNotFoundException("view display", display));
        if (!executor.mayAccess(config, display, authentication)) {
            throw new AccessDeniedException("You may not see this listing.");
        }
        Object requested = request.getAttribute(ViewPageFilter.REQUESTED_PATH);
        String path = (requested instanceof String asked) ? asked : ViewPaths.address(shown);
        @SuppressWarnings("unchecked")
        List<String> arguments = (request.getAttribute(ViewPageFilter.ARGUMENTS) instanceof List<?> given)
                ? (List<String>) given : List.of();
        ViewRenderer.Rendered rendered = cache.render(config, display, arguments, new LinkedHashMap<>(input), path,
                !shown.flag(ViewDisplay.EXPOSED_BLOCK), authentication);
        SiteInformation site = configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class,
                SiteInformation.DEFAULTS);
        if (shown.plugin().equals(ViewDisplay.FEED)) {
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/rss+xml"))
                    .body(feed(site, shown, rendered.result().rows(), path));
        }
        String title = shown.title().isEmpty() ? config.label() : shown.title();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(pages.render(PageChrome.of(site.name(), title),
                Renderable.of("markup").with("value", rendered.html()), BlockContext.of(path, title)).html());
    }

    private String feed(SiteInformation site, ViewDisplay display, List<ResultRow> rows, String path) {
        StringBuilder items = new StringBuilder();
        for (ResultRow row : rows) {
            String link = site.url() + aliases.outbound(entityTypes.require(row.entity().entityType())
                    .canonicalPath(row.entity().id()));
            items.append("<item><title>").append(HtmlUtils.htmlEscape(row.entity().label())).append("</title><link>")
                    .append(HtmlUtils.htmlEscape(link)).append("</link><guid>").append(HtmlUtils.htmlEscape(link))
                    .append("</guid>");
            if (row.entity().fields().get(BaseFieldDefinition.CREATED) instanceof OffsetDateTime created) {
                items.append("<pubDate>").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                                created.withOffsetSameInstant(ZoneOffset.UTC)))
                        .append("</pubDate>");
            }
            items.append("</item>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>"
                + HtmlUtils.htmlEscape(display.title()) + "</title><link>" + HtmlUtils.htmlEscape(site.url() + path)
                + "</link><description>" + HtmlUtils.htmlEscape(site.name()) + "</description>" + items
                + "</channel></rss>";
    }
}
