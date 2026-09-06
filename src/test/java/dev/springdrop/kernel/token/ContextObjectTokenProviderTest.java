package dev.springdrop.kernel.token;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.reflect.PropertyReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextObjectTokenProviderTest {

    public record Author(String name) {
    }

    public record Node(String title, Author author) {

        public String getLegacyTitle() {
            return title;
        }
    }

    private static final PropertyReader PROPERTY_READER = new PropertyReader();

    private final NodeTokenProvider provider = new NodeTokenProvider(PROPERTY_READER);

    private String resolve(String name, Object node) {
        return provider.resolve(name, TokenContext.of(Map.of("node", node)));
    }

    @Test
    void resolvesAPropertyOfTheContextObject() {
        assertThat(resolve("title", new Node("Hello", new Author("Alice")))).isEqualTo("Hello");
    }

    @Test
    void resolvesAChainedPropertyThroughTheObjectGraph() {
        assertThat(resolve("author:name", new Node("Hello", new Author("Alice")))).isEqualTo("Alice");
    }

    @Test
    void resolvesAJavaBeanGetter() {
        assertThat(resolve("legacyTitle", new Node("Hello", new Author("Alice")))).isEqualTo("Hello");
    }

    @Test
    void resolvesAKeyOfAMapContextObject() {
        assertThat(resolve("title", Map.of("title", "From a map"))).isEqualTo("From a map");
    }

    @Test
    void returnsNothingForAnUnknownProperty() {
        assertThat(resolve("subtitle", new Node("Hello", new Author("Alice")))).isNull();
    }

    @Test
    void returnsNothingWhenAChainBreaksOnANullPart() {
        assertThat(resolve("author:name", new Node("Hello", null))).isNull();
    }

    @Test
    void returnsNothingWhenTheContextHoldsNoSuchObject() {
        assertThat(provider.resolve("title", TokenContext.of(Map.of()))).isNull();
    }

    @Test
    void theTokenBrowserSeesTheProvidersTokens() {
        assertThat(provider.availableTokens()).extracting(token -> token.name()).contains("title", "author:name");
    }

    @Test
    void eachEntityProviderResolvesUnderItsOwnContextKey() {
        List<ContextObjectTokenProvider> providers = List.of(
                new NodeTokenProvider(PROPERTY_READER), new UserTokenProvider(PROPERTY_READER),
                new TermTokenProvider(PROPERTY_READER), new FileTokenProvider(PROPERTY_READER));

        assertThat(providers).allSatisfy(entityProvider -> {
            TokenContext context = TokenContext.of(Map.of(entityProvider.type(), Map.of("name", "Subject")));
            assertThat(entityProvider.resolve("name", context)).isEqualTo("Subject");
            assertThat(entityProvider.availableTokens()).isNotEmpty();
        });
    }
}
