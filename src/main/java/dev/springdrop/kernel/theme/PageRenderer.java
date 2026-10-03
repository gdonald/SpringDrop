package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.cache.CacheTags;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuTreeBuilder;
import dev.springdrop.kernel.render.Attachments;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.kernel.render.Shell;
import dev.springdrop.kernel.site.SiteInformation;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.HtmlUtils;

/**
 * Draws a whole page: the active theme's layout wrapped around the content a
 * controller produced, with the chrome the page asked for.
 *
 * <p>The content is a child of the layout, so what it depends on and what it asks
 * the page to attach bubble up through the layout to the page.
 *
 * <p>The layout draws the shared partials by path rather than by name, so a theme
 * overrides the pager or the messages without copying the layout that includes
 * them.
 *
 * <p>Each region the chrome carries is a child of the layout in a slot named after
 * the region, so the layout draws {@code slots['sidebar']} where the sidebar goes
 * and the blocks in it bubble their metadata like the content does.
 */
@Service
public class PageRenderer {

    public static final String LAYOUT_DIRECTORY = "layout";
    public static final String PARTIAL_DIRECTORY = "partials";
    public static final String LAYOUT = "page";

    /** The request attribute holding the page drawn for the request, for the page caches, when it may be kept. */
    public static final String PAGE_ATTRIBUTE = PageRenderer.class.getName() + ".page";

    /** A page as drawn: its shell, with the placeholders as markers, and the finished page. */
    public record Drawn(Shell shell, RenderedPage page) {
    }

    /** The partials every layout draws, each overridable on its own. */
    public static final List<String> PARTIALS =
            List.of("menu", "messages", "breadcrumb", "tabs", "local-actions", "pager");

    private final ThemeService themes;
    private final ThemeRegistry registry;
    private final RenderService renderer;
    private final BigPipe bigPipe;

    public PageRenderer(ThemeService themes, ThemeRegistry registry, RenderService renderer, BigPipe bigPipe) {
        this.themes = themes;
        this.registry = registry;
        this.renderer = renderer;
        this.bigPipe = bigPipe;
    }

    public RenderedPage render(PageChrome chrome, Renderable content) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("siteName", chrome.siteName());
        variables.put("slogan", chrome.slogan());
        variables.put("title", chrome.title());
        variables.put("primaryNavigation", chrome.primaryNavigation());
        variables.put("breadcrumbs", chrome.breadcrumbs());
        variables.put("tabs", chrome.tabs());
        variables.put("localActions", chrome.localActions());
        variables.put("messages", chrome.messages());
        variables.put("pager", chrome.pager().orElse(Pager.NONE));
        variables.put("partials", partialTemplates());

        Renderable layout = themes.build(LAYOUT_DIRECTORY, TemplateSuggestions.of(LAYOUT), variables);
        for (Map.Entry<String, Renderable> region : chrome.regions().entrySet()) {
            layout = layout.child(region.getValue().inSlot(region.getKey()));
        }
        Shell shell = renderer.renderShell(layout.child(content).cacheability(chromeCacheability(chrome)));
        RenderedPage page = finish(shell);
        if (chrome.pageCacheable()
                && RequestContextHolder.getRequestAttributes() instanceof RequestAttributes request) {
            request.setAttribute(PAGE_ATTRIBUTE, new Drawn(shell, page), RequestAttributes.SCOPE_REQUEST);
        }
        return page;
    }

    /**
     * Builds a page shell's placeholders, or for a streamed page with
     * placeholders leaves them to be sent after it, and writes what the page
     * carries into its head and body.
     */
    public RenderedPage finish(Shell shell) {
        RenderedPage page = (bigPipe.active() && !shell.placeholders().isEmpty()) ? bigPipe.defer(shell)
                : renderer.fill(shell);
        return new RenderedPage(withAttachments(page.html(), page.attachments()), page.cache(), page.attachments());
    }

    /**
     * What the chrome depends on: the site's name and slogan, and the main
     * menu the primary navigation is drawn from. Status messages are for one
     * request, so a page showing them may not be cached.
     */
    static CacheMetadata chromeCacheability(PageChrome chrome) {
        CacheMetadata cache = MenuTreeBuilder.cacheability(MenuConfig.MAIN)
                .withTag(CacheTags.config(SiteInformation.CONFIG_NAME));
        return chrome.messages().isEmpty() ? cache : cache.withMaxAge(Duration.ZERO);
    }

    /**
     * Writes what the page's parts asked it to carry: style sheets and other
     * head tags at the end of the head, scripts at the end of the body.
     */
    static String withAttachments(String html, Attachments attachments) {
        StringBuilder head = new StringBuilder();
        attachments.styleSheets().forEach(href -> head.append("<link rel=\"stylesheet\" href=\"")
                .append(HtmlUtils.htmlEscape(href)).append("\">"));
        attachments.headTags().forEach(head::append);
        StringBuilder body = new StringBuilder();
        attachments.scripts().forEach(src -> body.append("<script type=\"module\" src=\"")
                .append(HtmlUtils.htmlEscape(src)).append("\"></script>"));
        return html.replace("</head>", head + "</head>").replace("</body>", body + "</body>");
    }

    private Map<String, String> partialTemplates() {
        Map<String, String> paths = new LinkedHashMap<>();
        for (String partial : PARTIALS) {
            paths.put(partial, registry.resolve(PARTIAL_DIRECTORY, TemplateSuggestions.of(partial))
                    .orElseThrow(() -> new IllegalStateException(
                            "The active theme has no " + partial + " partial")));
        }
        return paths;
    }
}
