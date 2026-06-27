package dev.springdrop.kernel.routing;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the resolved theme to every view as the {@code theme} model attribute,
 * so templates render under the admin or front-end theme for the current route.
 */
@ControllerAdvice
public class ThemeModelAdvice {

    private final ThemeResolver themeResolver;

    public ThemeModelAdvice(ThemeResolver themeResolver) {
        this.themeResolver = themeResolver;
    }

    @ModelAttribute("theme")
    public String theme(HttpServletRequest request) {
        return themeResolver.resolve(request.getRequestURI());
    }
}
