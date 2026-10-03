package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.user.AccountPrincipals;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The values of cache contexts for the current request, which a cached entry
 * varies by. Outside a request the request-based contexts read as empty.
 *
 * <table>
 *   <caption>Contexts</caption>
 *   <tr><td>{@code user}</td><td>the account's name, empty for someone not signed in</td></tr>
 *   <tr><td>{@code user.roles}</td><td>the roles held</td></tr>
 *   <tr><td>{@code user.permissions}</td><td>the authorities held</td></tr>
 *   <tr><td>{@code languages}</td><td>the request's language</td></tr>
 *   <tr><td>{@code url}</td><td>the path and query string</td></tr>
 *   <tr><td>{@code url.path} and {@code route}</td><td>the path</td></tr>
 *   <tr><td>{@code url.query_args}</td><td>every query argument</td></tr>
 *   <tr><td>{@code url.query_args:<name>}</td><td>one query argument</td></tr>
 * </table>
 */
@Component
public class CacheContexts {

    public static final String USER = "user";
    public static final String USER_ROLES = "user.roles";
    public static final String USER_PERMISSIONS = "user.permissions";
    public static final String LANGUAGES = "languages";
    public static final String URL = "url";
    public static final String URL_PATH = "url.path";
    public static final String ROUTE = "route";
    public static final String QUERY_ARGS = "url.query_args";

    private static final String QUERY_ARG_PREFIX = QUERY_ARGS + ":";

    /** Each context with its value, in the contexts' order, so the same values give the same text. */
    public String values(Collection<String> contexts) {
        Map<String, String> values = new TreeMap<>();
        contexts.forEach(context -> values.put(context, value(context)));
        return values.toString();
    }

    public String value(String context) {
        Authentication reader = SecurityContextHolder.getContext().getAuthentication();
        Optional<HttpServletRequest> request = request();
        if (context.startsWith(QUERY_ARG_PREFIX)) {
            String name = context.substring(QUERY_ARG_PREFIX.length());
            return request.map(each -> String.join(",", Optional.ofNullable(each.getParameterValues(name))
                    .orElse(new String[0]))).orElse("");
        }
        return switch (context) {
            case USER -> signedIn(reader) ? reader.getName() : "";
            case USER_ROLES -> String.join(",", AccountPrincipals.rolesOf(reader).stream().sorted().toList());
            case USER_PERMISSIONS -> signedIn(reader) ? reader.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority).sorted().collect(Collectors.joining(",")) : "";
            case LANGUAGES -> LocaleContextHolder.getLocale().toLanguageTag();
            case URL -> request.map(each -> path(each) + "?" + Optional.ofNullable(each.getQueryString())
                    .orElse("")).orElse("");
            case URL_PATH, ROUTE -> request.map(CacheContexts::path).orElse("");
            case QUERY_ARGS -> request.map(each -> new TreeMap<>(each.getParameterMap()).entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + Arrays.toString(entry.getValue()))
                    .collect(Collectors.joining("&"))).orElse("");
            default -> throw new IllegalArgumentException("The site has no cache context " + context + ".");
        };
    }

    private static boolean signedIn(Authentication reader) {
        return reader != null && !(reader instanceof AnonymousAuthenticationToken);
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static Optional<HttpServletRequest> request() {
        return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                ? Optional.of(attributes.getRequest()) : Optional.empty();
    }
}
