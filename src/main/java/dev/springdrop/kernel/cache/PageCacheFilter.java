package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.render.Shell;
import dev.springdrop.kernel.security.SecurityHeadersFilter;
import dev.springdrop.kernel.theme.PageRenderer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Answers reads of pages from the {@link PageCache}. It runs after sign-in is
 * known and the theme is chosen. A read by someone not signed in, who has no
 * session, is answered with the finished page; a read by someone signed in is
 * answered with the page's shell, its named placeholders built for them. Each
 * answer carries {@code X-SpringDrop-Cache} as {@code HIT} or {@code MISS}.
 *
 * <p>A page is kept only when it was drawn by the page renderer, answered 200,
 * and may be cached. A page holding a form's CSRF token is not kept, since the
 * token belongs to one session, and neither is a page for someone not signed
 * in whose drawing started a session. The nonce a kept page's inline scripts
 * carry is replaced with the one minted for the request answered.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 50)
public class PageCacheFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-SpringDrop-Cache";

    private static final String CSRF_FIELD = "name=\"_csrf\"";

    private final PageCache pages;
    private final PageRenderer renderer;

    public PageCacheFilter(PageCache pages, PageRenderer renderer) {
        this.pages = pages;
        this.renderer = renderer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean reading = request.getMethod().equals("GET") || request.getMethod().equals("HEAD");
        boolean signedIn = signedIn(SecurityContextHolder.getContext().getAuthentication());
        if (!reading || (!signedIn && request.getSession(false) != null)) {
            chain.doFilter(request, response);
            return;
        }
        String address = address(request);
        String nonce = String.valueOf(request.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE));
        if (signedIn) {
            Optional<PageCache.Unfinished> kept = pages.unfinished(address);
            if (kept.isPresent()) {
                answer(response, kept.get().contentType(), renderer.finish(kept.get().shell()).html(),
                        kept.get().nonce(), nonce);
                return;
            }
        } else {
            Optional<PageCache.Finished> kept = pages.finished(address);
            if (kept.isPresent()) {
                answer(response, kept.get().contentType(), kept.get().html(), kept.get().nonce(), nonce);
                return;
            }
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, wrapper);
        keep(request, wrapper, address, nonce, signedIn);
        wrapper.copyBodyToResponse();
    }

    private void keep(HttpServletRequest request, ContentCachingResponseWrapper response, String address,
            String nonce, boolean signedIn) {
        if (!(request.getAttribute(PageRenderer.PAGE_ATTRIBUTE) instanceof PageRenderer.Drawn drawn)
                || response.getStatus() != HttpStatus.OK.value()) {
            return;
        }
        response.setHeader(HEADER, "MISS");
        String contentType = response.getContentType();
        if (signedIn) {
            Shell shell = drawn.shell();
            if (shell.reusable() && !shell.html().contains(CSRF_FIELD)) {
                pages.keepUnfinished(address, shell.cache(), new PageCache.Unfinished(shell, contentType, nonce));
            }
        } else {
            String html = new String(response.getContentAsByteArray(), StandardCharsets.UTF_8);
            if (request.getSession(false) == null && !html.contains(CSRF_FIELD)) {
                pages.keepFinished(address, drawn.page().cache(), new PageCache.Finished(html, contentType, nonce));
            }
        }
    }

    private static void answer(HttpServletResponse response, String contentType, String html, String keptNonce,
            String nonce) throws IOException {
        response.setHeader(HEADER, "HIT");
        response.setContentType(contentType);
        byte[] body = html.replace(keptNonce, nonce).getBytes(StandardCharsets.UTF_8);
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static boolean signedIn(Authentication reader) {
        return reader != null && !(reader instanceof AnonymousAuthenticationToken);
    }

    /**
     * The path the reader asked for, before any filter answered it as another,
     * with the query arguments in order of name.
     */
    private static String address(HttpServletRequest request) {
        ServletRequest asked = request;
        while (asked instanceof ServletRequestWrapper wrapper) {
            asked = wrapper.getRequest();
        }
        HttpServletRequest original = (HttpServletRequest) asked;
        return original.getRequestURI() + new TreeMap<>(original.getParameterMap()).entrySet().stream()
                .map(argument -> argument.getKey() + "=" + Arrays.toString(argument.getValue()))
                .collect(Collectors.joining("&", "?", ""));
    }
}
