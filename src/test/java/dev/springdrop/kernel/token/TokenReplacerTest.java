package dev.springdrop.kernel.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TokenReplacerTest {

    private final TokenReplacer replacer = new TokenReplacer(List.of(
            provider("site", name -> "name".equals(name) ? "My Site" : null),
            provider("node", name -> "author:name".equals(name) ? "Alice" : null),
            provider("danger", name -> "<b>x</b>")));

    @Test
    void replacesASimpleToken() {
        assertThat(replacer.replace("Welcome to [site:name]", TokenContext.of(Map.of())))
                .isEqualTo("Welcome to My Site");
    }

    @Test
    void resolvesAChainedTokenThroughItsProvider() {
        assertThat(replacer.replace("By [node:author:name]", TokenContext.of(Map.of())))
                .isEqualTo("By Alice");
    }

    @Test
    void leavesUnknownTokensInPlaceByDefault() {
        assertThat(replacer.replace("[unknown:thing] and [site:missing]", TokenContext.of(Map.of())))
                .isEqualTo("[unknown:thing] and [site:missing]");
    }

    @Test
    void clearsUnknownTokensWhenRequested() {
        TokenContext clearing = new TokenContext(Map.of(), false, true);
        assertThat(replacer.replace("a[unknown:thing]b", clearing)).isEqualTo("ab");
    }

    @Test
    void escapesValuesInSanitizeMode() {
        TokenContext sanitizing = new TokenContext(Map.of(), true, false);
        assertThat(replacer.replace("[danger:x]", sanitizing)).isEqualTo("&lt;b&gt;x&lt;/b&gt;");
    }

    private TokenProvider provider(String type, java.util.function.Function<String, String> resolver) {
        return new TokenProvider() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public String resolve(String name, TokenContext context) {
                return resolver.apply(name);
            }
        };
    }
}
