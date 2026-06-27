package dev.springdrop.kernel.web;

import dev.springdrop.kernel.routing.EntityUpcastRegistry;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Upcasts a route's path value into the entity a controller parameter expects.
 * A method parameter typed as a registered entity is loaded from the path
 * variable that shares its name; a missing entity yields a themed 404.
 */
public class EntityArgumentResolver implements HandlerMethodArgumentResolver {

    private final EntityUpcastRegistry registry;

    public EntityArgumentResolver(EntityUpcastRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return registry.supports(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        @SuppressWarnings("unchecked")
        Map<String, String> uriVariables = (Map<String, String>) webRequest.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        String id = uriVariables.get(parameter.getParameterName());

        return registry.load(parameter.getParameterType(), id)
                .orElseThrow(() -> new EntityNotFoundException(
                        parameter.getParameterType().getSimpleName(), id));
    }
}
