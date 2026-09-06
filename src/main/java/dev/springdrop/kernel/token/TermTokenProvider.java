package dev.springdrop.kernel.token;

import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [term:...]} tokens against the taxonomy term in context, under the
 * {@code term} key of the token context. The listed tokens are the ones the
 * token browser shows; any readable property of the object resolves.
 */
@Component
public class TermTokenProvider extends ContextObjectTokenProvider {

    public TermTokenProvider(PropertyReader propertyReader) {
        super(propertyReader);
    }

    @Override
    public String type() {
        return "term";
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
            new TokenDefinition("name", "The term's name"),
            new TokenDefinition("vocabulary:name", "The name of the term's vocabulary"));
    }
}
