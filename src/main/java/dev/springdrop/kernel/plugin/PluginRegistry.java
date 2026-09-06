package dev.springdrop.kernel.plugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link PluginManager} for a plugin contract by discovering every
 * {@link SpringDropPlugin}-annotated bean of that type, expanding any
 * {@link DerivablePlugin} into its derivatives. Managers are cached per type.
 */
@Component
public class PluginRegistry {

    private final ApplicationContext context;
    private final Map<Class<?>, PluginManager<?>> cache = new ConcurrentHashMap<>();

    public PluginRegistry(ApplicationContext context) {
        this.context = context;
    }

    public <T> PluginManager<T> managerFor(Class<T> pluginType) {
        PluginManager<?> cached = cache.get(pluginType);
        if (cached != null) {
            return cached.as(pluginType);
        }
        PluginManager<?> manager = build(pluginType);
        cache.put(pluginType, manager);
        return manager.as(pluginType);
    }

    private PluginManager<?> build(Class<?> pluginType) {
        Map<String, Supplier<?>> index = new LinkedHashMap<>();
        for (String beanName : context.getBeanNamesForAnnotation(SpringDropPlugin.class)) {
            SpringDropPlugin annotation = context.findAnnotationOnBean(beanName, SpringDropPlugin.class);
            if (!pluginType.equals(annotation.type())) {
                continue;
            }
            register(index, beanName, annotation, pluginType);
        }
        return new PluginManager<>(pluginType, index);
    }

    private void register(
            Map<String, Supplier<?>> index, String beanName, SpringDropPlugin annotation, Class<?> pluginType) {

        if (DerivablePlugin.class.isAssignableFrom(context.getType(beanName))) {
            DerivablePlugin<?> derivable = (DerivablePlugin<?>) context.getBean(beanName);
            derivable.derivatives().forEach((suffix, instance) ->
                    index.put(annotation.id() + ":" + suffix, () -> instance));
        } else {
            index.put(annotation.id(), () -> context.getBean(beanName, pluginType));
        }
    }
}
