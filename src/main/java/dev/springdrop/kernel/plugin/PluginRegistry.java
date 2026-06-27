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

    @SuppressWarnings("unchecked")
    public <T> PluginManager<T> managerFor(Class<T> pluginType) {
        PluginManager<?> cached = cache.get(pluginType);
        if (cached != null) {
            return (PluginManager<T>) cached;
        }
        PluginManager<T> manager = build(pluginType);
        cache.put(pluginType, manager);
        return manager;
    }

    private <T> PluginManager<T> build(Class<T> pluginType) {
        Map<String, Supplier<T>> index = new LinkedHashMap<>();
        for (String beanName : context.getBeanNamesForAnnotation(SpringDropPlugin.class)) {
            SpringDropPlugin annotation = context.findAnnotationOnBean(beanName, SpringDropPlugin.class);
            if (!pluginType.equals(annotation.type())) {
                continue;
            }
            register(index, beanName, annotation, pluginType);
        }
        return new PluginManager<>(index);
    }

    @SuppressWarnings("unchecked")
    private <T> void register(
            Map<String, Supplier<T>> index, String beanName, SpringDropPlugin annotation, Class<T> pluginType) {

        if (DerivablePlugin.class.isAssignableFrom(context.getType(beanName))) {
            DerivablePlugin<T> derivable = (DerivablePlugin<T>) context.getBean(beanName);
            derivable.derivatives().forEach((suffix, instance) ->
                    index.put(annotation.id() + ":" + suffix, () -> instance));
        } else {
            index.put(annotation.id(), () -> context.getBean(beanName, pluginType));
        }
    }
}
