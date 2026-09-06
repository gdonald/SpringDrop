package dev.springdrop.kernel.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The off-site hosts a redirect destination may name. Anything else is refused,
 * so a link carrying {@code ?destination=} cannot walk a signed-in visitor off
 * to another site.
 */
@ConfigurationProperties("springdrop.security.redirect")
public record RedirectProperties(@DefaultValue List<String> allowedHosts) {
}
