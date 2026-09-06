package dev.springdrop.kernel.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The security headers every response carries. The content security policy is a
 * template: {@code {nonce}} in it is replaced with the nonce minted for that one
 * response, which is the only value an inline script may carry. A blank value
 * leaves that header off.
 */
@ConfigurationProperties("springdrop.security.headers")
public record SecurityHeadersProperties(
        @DefaultValue("SAMEORIGIN") String frameOptions,
        @DefaultValue("nosniff") String contentTypeOptions,
        @DefaultValue("strict-origin-when-cross-origin") String referrerPolicy,
        @DefaultValue(DEFAULT_POLICY) String contentSecurityPolicy) {

    public static final String NONCE_PLACEHOLDER = "{nonce}";

    static final String DEFAULT_POLICY = "default-src 'self'; "
            + "script-src 'self' 'nonce-{nonce}' https://cdn.jsdelivr.net; "
            + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
            + "img-src 'self' data:; "
            + "frame-ancestors 'self'";

    public String policyWith(String nonce) {
        return contentSecurityPolicy.replace(NONCE_PLACEHOLDER, nonce);
    }
}
