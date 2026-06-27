package dev.springdrop.kernel.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.stereotype.Component;

/**
 * Marks a class as a plugin with an {@code id} and a contract {@code type} (the
 * interface it implements, such as a field type or block). Because it is a
 * component, an annotated class is also a Spring bean, so a module contributes a
 * plugin just by annotating it. The {@link PluginRegistry} discovers them by type.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface SpringDropPlugin {

    String id();

    Class<?> type();
}
