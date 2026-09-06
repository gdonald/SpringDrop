package dev.springdrop.kernel.token;

import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Resolves {@code [file:...]} tokens against the file in context, under the
 * {@code file} key of the token context. The listed tokens are the ones the
 * token browser shows; any readable property of the object resolves.
 */
@Component
public class FileTokenProvider extends ContextObjectTokenProvider {

    public FileTokenProvider(PropertyReader propertyReader) {
        super(propertyReader);
    }

    @Override
    public String type() {
        return "file";
    }

    @Override
    public List<TokenDefinition> availableTokens() {
        return List.of(
            new TokenDefinition("name", "The file's name"),
            new TokenDefinition("size", "The file's size in bytes"),
            new TokenDefinition("url", "The file's URL"));
    }
}
