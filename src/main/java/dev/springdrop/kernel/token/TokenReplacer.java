package dev.springdrop.kernel.token;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Replaces {@code [type:name]} tokens in text using the registered
 * {@link TokenProvider}s. Unknown tokens are left in place or cleared per the
 * context, and values are HTML-escaped in sanitize mode.
 */
@Component
public class TokenReplacer {

    private static final Pattern TOKEN = Pattern.compile("\\[([a-z0-9_]+):([a-z0-9_:.\\-]+)]");

    private final Map<String, TokenProvider> providers;

    public TokenReplacer(List<TokenProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(TokenProvider::type, Function.identity()));
    }

    public String replace(String text, TokenContext context) {
        Matcher matcher = TOKEN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = resolve(matcher.group(1), matcher.group(2), matcher.group(0), context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String resolve(String type, String name, String original, TokenContext context) {
        TokenProvider provider = providers.get(type);
        String value = (provider == null) ? null : provider.resolve(name, context);
        if (value == null) {
            return context.clearUnknown() ? "" : original;
        }
        return context.sanitize() ? HtmlUtils.htmlEscape(value) : value;
    }
}
