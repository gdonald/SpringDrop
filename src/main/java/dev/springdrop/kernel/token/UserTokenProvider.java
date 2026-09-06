package dev.springdrop.kernel.token;

import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [user:...]} tokens against the user in context, under the
 * {@code user} key of the token context. The listed tokens are the ones the
 * token browser shows; any readable property of the object resolves.
 */
@Component
public class UserTokenProvider extends ContextObjectTokenProvider {

    public UserTokenProvider(PropertyReader propertyReader) {
        super(propertyReader);
    }

    @Override
    public String type() {
        return "user";
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
            new TokenDefinition("name", "The user's name"),
            new TokenDefinition("mail", "The user's email address"));
    }
}
