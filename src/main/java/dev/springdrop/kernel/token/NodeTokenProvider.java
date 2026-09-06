package dev.springdrop.kernel.token;

import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [node:...]} tokens against the node in context, under the
 * {@code node} key of the token context. The listed tokens are the ones the
 * token browser shows; any readable property of the object resolves.
 */
@Component
public class NodeTokenProvider extends ContextObjectTokenProvider {

    public NodeTokenProvider(PropertyReader propertyReader) {
        super(propertyReader);
    }

    @Override
    public String type() {
        return "node";
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
            new TokenDefinition("title", "The node's title"),
            new TokenDefinition("author:name", "The name of the node's author"),
            new TokenDefinition("created", "When the node was created"));
    }
}
