package dev.springdrop.kernel.web;

import dev.springdrop.kernel.config.ConfigStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/**
 * Serves a site-configured page for 403 and 404 responses by forwarding to the
 * configured internal path, keeping the original status code. Returning null
 * leaves the themed default template in place, so an unconfigured site and every
 * other status render as before. The forward happens at most once per request,
 * so a configured path that itself fails falls back to the default page instead
 * of looping.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SiteErrorViewResolver implements ErrorViewResolver {

    private static final String FORWARDED = SiteErrorViewResolver.class.getName() + ".forwarded";

    private final ConfigStore configStore;

    public SiteErrorViewResolver(ConfigStore configStore) {
        this.configStore = configStore;
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        if (status != HttpStatus.FORBIDDEN && status != HttpStatus.NOT_FOUND) {
            return null;
        }
        if (request.getAttribute(FORWARDED) != null) {
            return null;
        }

        ErrorPagesConfig config = configStore.read(
                ErrorPagesConfig.CONFIG_NAME, ErrorPagesConfig.class, ErrorPagesConfig.DEFAULTS);
        String path = (status == HttpStatus.FORBIDDEN) ? config.forbiddenPath() : config.notFoundPath();
        if (path == null || path.isBlank()) {
            return null;
        }

        request.setAttribute(FORWARDED, Boolean.TRUE);
        return new ModelAndView("forward:" + path, model);
    }
}
