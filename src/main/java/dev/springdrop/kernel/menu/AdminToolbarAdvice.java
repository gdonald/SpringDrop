package dev.springdrop.kernel.menu;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives every view the administration toolbar as the {@code toolbar} model
 * attribute. The toolbar's menu is built only when a template draws it, so a
 * response that draws no page builds nothing.
 */
@ControllerAdvice
public class AdminToolbarAdvice {

    private final AdminToolbar toolbar;

    public AdminToolbarAdvice(AdminToolbar toolbar) {
        this.toolbar = toolbar;
    }

    @ModelAttribute("toolbar")
    public AdminToolbar.View toolbar(HttpServletRequest request) {
        return toolbar.view(request.getRequestURI());
    }
}
