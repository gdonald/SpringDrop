package dev.springdrop.kernel.views;

import dev.springdrop.kernel.web.ThemeNegotiationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Answers a read of a page or feed display's path as a request for the
 * display's own address, {@code /views/page/<view>/<display>}, with the path's
 * contextual filter values and the path asked for kept on the request. It runs
 * after aliases, so an alias can stand for a display's path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class ViewPageFilter extends OncePerRequestFilter {

    public static final String PAGE_PREFIX = "/views/page/";

    /** The request attribute holding the contextual filters' values. */
    public static final String ARGUMENTS = ViewPageFilter.class.getName() + ".arguments";

    /** The request attribute holding the path the reader asked for, which the theme follows too. */
    public static final String REQUESTED_PATH = ThemeNegotiationFilter.REQUESTED_PATH;

    private final ViewPaths paths;

    public ViewPageFilter(ViewPaths paths) {
        this.paths = paths;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean reading = request.getMethod().equals("GET") || request.getMethod().equals("HEAD");
        Optional<ViewPaths.Match> match = reading ? paths.match(path) : Optional.empty();
        if (match.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(ARGUMENTS, match.get().arguments());
        request.setAttribute(REQUESTED_PATH, path);
        String target = PAGE_PREFIX + match.get().view().id() + "/" + match.get().display().id();
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override
            public String getRequestURI() {
                return getContextPath() + target;
            }

            @Override
            public String getServletPath() {
                return target;
            }

            @Override
            public String getPathInfo() {
                return null;
            }
        }, response);
    }
}
