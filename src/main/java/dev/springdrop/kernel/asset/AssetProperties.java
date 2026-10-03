package dev.springdrop.kernel.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Whether pages load the aggregated, fingerprinted bundle of the site's scripts
 * rather than each module, and the bundle's address, which the build writes to
 * {@code springdrop/assets.properties}.
 */
@ConfigurationProperties("springdrop.assets")
public record AssetProperties(boolean aggregate, String bundle) {
}
