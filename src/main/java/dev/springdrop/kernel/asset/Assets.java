package dev.springdrop.kernel.asset;

import org.springframework.stereotype.Component;

/**
 * The address a page loads a script module from. With aggregation on, every
 * module's address is the bundle the build made, which holds each module's
 * {@code init} functions minified into one file named with a hash of its
 * content. With it off, each module is loaded from its own address. Templates
 * ask through {@code ${@assets.url('/js/<module>.js')}}.
 */
@Component("assets")
public class Assets {

    public static final String BUNDLE_PREFIX = "/assets/";

    private final String bundle;

    public Assets(AssetProperties properties) {
        if (properties.aggregate() && properties.bundle() == null) {
            throw new IllegalStateException("Asset aggregation is on, but the build named no bundle in "
                    + "springdrop/assets.properties.");
        }
        this.bundle = properties.aggregate() ? properties.bundle() : null;
    }

    public String url(String module) {
        return bundle == null ? module : bundle;
    }
}
