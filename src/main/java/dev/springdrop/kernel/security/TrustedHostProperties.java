package dev.springdrop.kernel.security;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The host names this site answers to, as regular expressions matched against
 * the name the request was addressed to. An empty list answers to any host,
 * which suits local development. A production site lists its own names, so a
 * request carrying someone else's Host header cannot make the site write that
 * name into a password reset link.
 */
@ConfigurationProperties("springdrop.security.trusted-host")
public record TrustedHostProperties(@DefaultValue List<String> patterns) {

    public boolean trusts(String host) {
        if (patterns.isEmpty()) {
            return true;
        }
        String name = (host == null) ? "" : host;
        return patterns.stream()
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .anyMatch(pattern -> pattern.matcher(name).matches());
    }
}
