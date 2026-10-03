package dev.springdrop.kernel.views;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Draws a display of a view inside another page, such as the front page's
 * listing, for the person making the request. A view the site no longer has,
 * or one the person may not see, draws nothing.
 */
@Component
public class ViewEmbed {

    private final ViewManager views;
    private final ViewExecutor executor;
    private final ViewCache cache;

    public ViewEmbed(ViewManager views, ViewExecutor executor, ViewCache cache) {
        this.views = views;
        this.executor = executor;
        this.cache = cache;
    }

    public Optional<ViewRenderer.Rendered> render(String viewId, String displayId, List<String> arguments,
            Map<String, String> input, String path, boolean exposedForm) {
        Authentication reader = SecurityContextHolder.getContext().getAuthentication();
        return views.find(viewId)
                .filter(view -> executor.mayAccess(view, displayId, reader))
                .map(view -> cache.render(view, displayId, arguments, input, path, exposedForm, reader));
    }
}
