package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.render.LazyBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Sends a streamed page: the shell up to the end of its body, flushed, then
 * each placeholder {@link BigPipe} left pending as it is built, each flushed,
 * then the rest of the page. It runs inside the theme's filter, so the
 * placeholders are drawn in the request's theme, and outside the page caches,
 * so a page answered from them is streamed as well.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 70)
public class BigPipeFilter extends OncePerRequestFilter {

    private static final String BODY_END = "</body>";

    private final BigPipe bigPipe;

    public BigPipeFilter(BigPipe bigPipe) {
        this.bigPipe = bigPipe;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!bigPipe.active()) {
            chain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper shell = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, shell);
        Charset charset = Charset.forName(shell.getCharacterEncoding());
        String html = new String(shell.getContentAsByteArray(), charset);
        int bodyEnd = html.lastIndexOf(BODY_END);
        if (!(request.getAttribute(BigPipe.PENDING_ATTRIBUTE) instanceof BigPipe.Pending pending) || bodyEnd < 0) {
            shell.copyBodyToResponse();
            return;
        }
        ServletOutputStream out = response.getOutputStream();
        send(response, out, html.substring(0, bodyEnd), charset);
        for (Map.Entry<String, LazyBuilder> placeholder : pending.placeholders().entrySet()) {
            send(response, out, bigPipe.replacement(placeholder.getKey(), placeholder.getValue(),
                    pending.numbered()), charset);
        }
        send(response, out, html.substring(bodyEnd), charset);
    }

    private static void send(HttpServletResponse response, ServletOutputStream out, String part, Charset charset)
            throws IOException {
        out.write(part.getBytes(charset));
        response.flushBuffer();
    }
}
