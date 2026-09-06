package dev.springdrop.kernel.token;

import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.List;

/**
 * Base for providers that resolve tokens against an object the caller placed in
 * the token context under the provider's type, such as the node behind
 * {@code [node:title]}. A colon-separated name walks the object graph, so
 * {@code [node:author:name]} reads the node's author and then that author's name.
 */
public abstract class ContextObjectTokenProvider implements TokenProvider {

    private final PropertyReader propertyReader;

    protected ContextObjectTokenProvider(PropertyReader propertyReader) {
        this.propertyReader = propertyReader;
    }

    @Override
    public String resolve(String name, TokenContext context) {
        Object value = propertyReader.read(context.data().get(type()), List.of(name.split(":")));
        return (value == null) ? null : value.toString();
    }
}
