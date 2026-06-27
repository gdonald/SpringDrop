package dev.springdrop.kernel.web;

import dev.springdrop.kernel.routing.EntityUpcastRegistry;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the kernel's MVC extensions, currently the entity argument resolver
 * that performs route parameter upcasting.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EntityUpcastRegistry entityUpcastRegistry;

    public WebConfig(EntityUpcastRegistry entityUpcastRegistry) {
        this.entityUpcastRegistry = entityUpcastRegistry;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new EntityArgumentResolver(entityUpcastRegistry));
    }
}
