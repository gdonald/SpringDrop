package dev.springdrop.kernel.filter;

import java.util.Map;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * The last pass text takes before it reaches the page: the OWASP HTML
 * Sanitizer, keeping only the tags and attributes allowed, and links and images
 * only to http, https, and mailto addresses. It runs on every render, so
 * whatever was stored, or whatever an editor in a browser let through, is
 * cleaned again.
 */
@Component
public class HtmlSanitizer {

    public String sanitize(String html, AllowedHtml allowed) {
        return policy(allowed).sanitize(html);
    }

    private static PolicyFactory policy(AllowedHtml allowed) {
        HtmlPolicyBuilder builder = new HtmlPolicyBuilder().allowStandardUrlProtocols();
        for (Map.Entry<String, java.util.Set<String>> tag : allowed.tags().entrySet()) {
            builder.allowElements(tag.getKey());
            if (!tag.getValue().isEmpty()) {
                builder.allowAttributes(tag.getValue().toArray(String[]::new)).onElements(tag.getKey());
            }
        }
        return builder.toFactory();
    }
}
