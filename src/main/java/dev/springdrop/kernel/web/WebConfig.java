package dev.springdrop.kernel.web;

import dev.springdrop.kernel.asset.Assets;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.LocalDiskScheme;
import dev.springdrop.kernel.routing.EntityUpcastRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the kernel's MVC extensions: the entity argument resolver that
 * performs route parameter upcasting, the handler serving public files, and
 * the one serving the fingerprinted script bundle, which a browser may keep for
 * a year since its name changes with its content.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EntityUpcastRegistry entityUpcastRegistry;
    private final LocalDiskScheme publicFiles;

    public WebConfig(EntityUpcastRegistry entityUpcastRegistry, LocalDiskScheme publicFiles) {
        this.entityUpcastRegistry = entityUpcastRegistry;
        this.publicFiles = publicFiles;
    }

    /** Public files are served as they are, from the directory they are kept in. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(FileSchemes.PUBLIC_URL_PREFIX + "/**")
                .addResourceLocations(publicFiles.root().toUri().toString());
        registry.addResourceHandler(Assets.BUNDLE_PREFIX + "**")
                .addResourceLocations("classpath:/static" + Assets.BUNDLE_PREFIX)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new EntityArgumentResolver(entityUpcastRegistry));
    }
}
