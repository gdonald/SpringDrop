package dev.springdrop.kernel.reflect;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Reads a named property of an arbitrary object, following a path of names
 * through the object graph. A name resolves against a record accessor, a
 * JavaBean getter, or a map key, so tokens and constraints can address the same
 * value on an entity, a record, or a plain map.
 */
@Component
public class PropertyReader {

    public Object read(Object subject, List<String> path) {
        Object current = subject;
        for (String name : path) {
            if (current == null) {
                return null;
            }
            current = property(current, name);
        }
        return current;
    }

    private static Object property(Object target, String name) {
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        for (String accessor : List.of(name, "get" + capitalize(name))) {
            Method method = ReflectionUtils.findMethod(target.getClass(), accessor);
            if (method != null) {
                ReflectionUtils.makeAccessible(method);
                return ReflectionUtils.invokeMethod(method, target);
            }
        }
        return null;
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
