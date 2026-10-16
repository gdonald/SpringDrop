package dev.springdrop.kernel.install;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sends every request to the installer until the site is installed, except
 * the installer itself, the batch it runs, the files pages load, and the
 * error and health endpoints. Once the site is installed, the installer is
 * sent to the home page.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InstallerFilter extends OncePerRequestFilter {

    public static final String INSTALL_PATH = "/install";

    private static final List<String> OPEN_BEFORE_INSTALL = List.of(INSTALL_PATH, "/batch/", "/js/", "/assets/",
            "/css/", "/webjars/", "/favicon.ico", "/error", "/actuator");

    private final InstallState state;

    public InstallerFilter(InstallState state) {
        this.state = state;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean installing = path.equals(INSTALL_PATH) || path.startsWith(INSTALL_PATH + "/");
        boolean installed = state.installed();
        if (!installed && OPEN_BEFORE_INSTALL.stream().noneMatch(path::startsWith)) {
            response.sendRedirect(request.getContextPath() + INSTALL_PATH);
            return;
        }
        if (installed && installing) {
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }
        chain.doFilter(request, response);
    }
}
