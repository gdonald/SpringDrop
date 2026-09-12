package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.render.RenderedPage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

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
 */
@Service
public class PageRenderer {

    public static final String LAYOUT_DIRECTORY = "layout";
    public static final String PARTIAL_DIRECTORY = "partials";
    public static final String LAYOUT = "page";

    /** The partials every layout draws, each overridable on its own. */
    public static final List<String> PARTIALS =
            List.of("messages", "breadcrumb", "tabs", "local-actions", "pager");

    private final ThemeService themes;
    private final ThemeRegistry registry;
    private final RenderService renderer;

    public PageRenderer(ThemeService themes, ThemeRegistry registry, RenderService renderer) {
        this.themes = themes;
        this.registry = registry;
        this.renderer = renderer;
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
        return renderer.render(layout.child(content));
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
