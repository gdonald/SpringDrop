package dev.springdrop.kernel.asset;

import dev.springdrop.kernel.cache.Performance;
import org.springframework.stereotype.Component;

/**
 * The address a page loads a script module from. With aggregation on, every
 * module's address is the bundle the build made, which holds each module's
 * {@code init} functions minified into one file named with a hash of its
 * content. With it off, or with no bundle built, each module is loaded from
 * its own address. Whether aggregation is on is read from the performance
 * settings on each request. Templates ask through
 * {@code ${@assets.url('/js/<module>.js')}}.
 */
@Component("assets")
public class Assets {

    public static final String BUNDLE_PREFIX = "/assets/";

    private final String bundle;
    private final Performance performance;

    public Assets(AssetProperties properties, Performance performance) {
        if (properties.aggregate() && properties.bundle() == null) {
            throw new IllegalStateException("Asset aggregation is on, but the build named no bundle in "
                    + "springdrop/assets.properties.");
        }
        this.bundle = properties.bundle();
        this.performance = performance;
    }

    /** Whether the build made a bundle to aggregate the scripts into. */
    public boolean bundleBuilt() {
        return bundle != null;
    }

    public String url(String module) {
        return bundleBuilt() && performance.settings().aggregateJavaScript() ? bundle : module;
    }
}
