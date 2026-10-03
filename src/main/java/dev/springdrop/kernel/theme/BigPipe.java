package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.asset.Assets;
import dev.springdrop.kernel.render.Attachments;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.LazyBuilder;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.kernel.render.Shell;
import dev.springdrop.kernel.security.SecurityHeadersFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.HtmlUtils;

/**
 * Streams a page in two parts for someone signed in: the shell first, with a
 * marker for each placeholder, then each placeholder as it is built, as a JSON
 * data script that {@code /js/big-pipe.js} puts in the marker's place. A
 * browser without JavaScript follows the shell's {@code noscript} refresh to
 * {@link #NO_JS_PATH}, which sets the {@link #NO_JS_COOKIE} cookie, and pages
 * are drawn whole for it from then on.
 */
@Component
public class BigPipe {

    public static final String NO_JS_COOKIE = "springdrop-nojs";

    public static final String NO_JS_PATH = "/big-pipe/no-js";

    public static final String SCRIPT = "/js/big-pipe.js";

    public static final String PLACEHOLDER_ATTRIBUTE = "data-big-pipe-placeholder-id";

    public static final String REPLACEMENT_ATTRIBUTE = "data-big-pipe-replacement-for";

    /** The request attribute holding the placeholders a streamed page has yet to send. */
    public static final String PENDING_ATTRIBUTE = BigPipe.class.getName() + ".pending";

    /** The placeholders a streamed page has yet to send, and how many placeholders were numbered. */
    public record Pending(Map<String, LazyBuilder> placeholders, int numbered) {

        public Pending {
            placeholders = Map.copyOf(placeholders);
        }
    }

    private final RenderService renderer;
    private final Assets assets;

    public BigPipe(RenderService renderer, Assets assets) {
        this.renderer = renderer;
        this.assets = assets;
    }

    private static Optional<HttpServletRequest> request() {
        return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                ? Optional.of(attributes.getRequest()) : Optional.empty();
    }

    /** Whether the current request's page is streamed: a read by someone signed in whose browser runs scripts. */
    public boolean active() {
        Authentication reader = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = reader != null && !(reader instanceof AnonymousAuthenticationToken);
        return signedIn && request().filter(request -> request.getMethod().equals("GET"))
                .filter(request -> Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                        .noneMatch(cookie -> cookie.getName().equals(NO_JS_COOKIE)))
                .isPresent();
    }

    /**
     * The shell to send first, each placeholder marked for the script and
     * left on the request for {@link BigPipeFilter} to send after it. The
     * head loads the script, and sends a browser without JavaScript to
     * {@link #NO_JS_PATH}.
     */
    public RenderedPage defer(Shell shell) {
        HttpServletRequest request = request().orElseThrow();
        String html = shell.html();
        for (String id : shell.placeholders().keySet()) {
            html = html.replace(RenderService.marker(id), "<span " + PLACEHOLDER_ATTRIBUTE + "=\""
                    + HtmlUtils.htmlEscape(id) + "\"></span>");
        }
        request.setAttribute(PENDING_ATTRIBUTE, new Pending(shell.placeholders(), shell.numbered()));
        String nonce = String.valueOf(request.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE));
        String destination = request.getRequestURI() + (request.getQueryString() == null ? ""
                : "?" + request.getQueryString());
        String head = "<script type=\"module\" async nonce=\"" + HtmlUtils.htmlEscape(nonce) + "\">import { "
                + "initBigPipe } from '" + assets.url(SCRIPT) + "'; initBigPipe();</script><noscript><meta http-equiv=\"Refresh\" "
                + "content=\"0; URL=" + HtmlUtils.htmlEscape(NO_JS_PATH + "?destination="
                + URLEncoder.encode(destination, StandardCharsets.UTF_8))
                + "\"></noscript>";
        return new RenderedPage(html.replace("</head>", head + "</head>"), shell.cache(), shell.attachments());
    }

    /** One placeholder built, as the data script that replaces its marker. */
    public String replacement(String id, LazyBuilder builder, int numbered) {
        RenderedPage built = renderer.fill(new Shell(RenderService.marker(id), CacheMetadata.EMPTY, Attachments.NONE,
                Map.of(id, builder), numbered));
        StringBuilder html = new StringBuilder();
        built.attachments().styleSheets().forEach(href -> html.append("<link rel=\"stylesheet\" href=\"")
                .append(HtmlUtils.htmlEscape(href)).append("\">"));
        html.append(built.html());
        return "<script type=\"application/json\" " + REPLACEMENT_ATTRIBUTE + "=\"" + HtmlUtils.htmlEscape(id)
                + "\">{\"html\":\"" + json(html.toString()) + "\"}</script>";
    }

    /** Text as the inside of a JSON string, with nothing that could end the script holding it. */
    static String json(String text) {
        StringBuilder escaped = new StringBuilder();
        for (char character : text.toCharArray()) {
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '<', '>', '&' -> escaped.append(String.format("\\u%04x", (int) character));
                default -> escaped.append(character < 0x20 ? String.format("\\u%04x", (int) character)
                        : String.valueOf(character));
            }
        }
        return escaped.toString();
    }
}
