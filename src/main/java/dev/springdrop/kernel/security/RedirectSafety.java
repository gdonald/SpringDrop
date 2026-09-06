package dev.springdrop.kernel.security;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;

/**
 * Decides where a caller-supplied destination may send someone. A path on this
 * site is fine. An absolute URL is fine only when its host is allowlisted.
 * Anything else, including a protocol-relative {@code //elsewhere.example} and a
 * {@code javascript:} URL, falls back to the destination the caller nominated.
 */
@Component
public class RedirectSafety {

    private final RedirectProperties properties;

    public RedirectSafety(RedirectProperties properties) {
        this.properties = properties;
    }

    public String destinationOr(String candidate, String fallback) {
        return isSafe(candidate) ? candidate : fallback;
    }

    public boolean isSafe(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        if (candidate.startsWith("/")) {
            return isLocalPath(candidate);
        }
        return namesAnAllowedHost(candidate);
    }

    private static boolean isLocalPath(String candidate) {
        // A leading "//" or "/\" is read by browsers as a protocol-relative URL,
        // so it leaves the site despite looking like a path.
        return candidate.length() < 2 || (candidate.charAt(1) != '/' && candidate.charAt(1) != '\\');
    }

    private boolean namesAnAllowedHost(String candidate) {
        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException malformed) {
            return false;
        }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        return properties.allowedHosts().stream().anyMatch(allowed -> allowed.equalsIgnoreCase(host));
    }
}
