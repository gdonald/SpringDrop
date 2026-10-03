package dev.springdrop.kernel.path;

import dev.springdrop.kernel.entity.EntityData;
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
 * Answers a request for an alias as a request for its source. It runs before
 * the security chain, so access to an alias is decided by the page it stands
 * for, and everything after sees the source's path.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PathAliasFilter extends OncePerRequestFilter {

    private final PathAliasManager aliases;

    public PathAliasFilter(PathAliasManager aliases) {
        this.aliases = aliases;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<String> source = aliases.sourceOf(path, EntityData.DEFAULT_LANGCODE);
        chain.doFilter(source.isPresent() ? new SourceRequest(request, source.get()) : request, response);
    }

    /** The request as if it had asked for the source. */
    static final class SourceRequest extends HttpServletRequestWrapper {

        private final String source;

        SourceRequest(HttpServletRequest request, String source) {
            super(request);
            this.source = source;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + source;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer(super.getRequestURL());
            url.setLength(url.length() - super.getRequestURI().length());
            return url.append(getRequestURI());
        }

        @Override
        public String getServletPath() {
            return source;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    }
}
