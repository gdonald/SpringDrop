package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.cache.CacheContexts;
import dev.springdrop.kernel.render.PlaceholderBuilder;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.security.SecurityConfig;
import java.time.Duration;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.HtmlUtils;

/**
 * Names the account signed in, with a button signing out, or for someone not
 * signed in, a link to sign in. The greeting holds the session's CSRF token,
 * so it may not be cached, and as a placeholder it is built for each request a
 * kept page answers.
 */
@Component
public class AccountGreeting implements PlaceholderBuilder {

    public static final String ID = "account_greeting";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Renderable build(Map<String, String> arguments) {
        Authentication reader = SecurityContextHolder.getContext().getAuthentication();
        if (reader == null || reader instanceof AnonymousAuthenticationToken) {
            return Renderable.of("markup").with("value", "<a class=\"btn btn-primary btn-sm\" href=\""
                    + SecurityConfig.LOGIN_PATH + "\">Log in</a>");
        }
        return Renderable.of("markup").with("value", "<form class=\"account-greeting d-flex align-items-center "
                + "gap-2\" method=\"post\" action=\"" + SecurityConfig.LOGOUT_PATH + "\"><span>Signed in as "
                + HtmlUtils.htmlEscape(reader.getName()) + "</span>" + csrfField()
                + "<button class=\"btn btn-secondary btn-sm\" type=\"submit\">Log out</button></form>")
                .cacheContext(CacheContexts.USER).maxAge(Duration.ZERO);
    }

    private static String csrfField() {
        Object token = RequestContextHolder.getRequestAttributes() == null ? null
                : RequestContextHolder.getRequestAttributes().getAttribute(CsrfToken.class.getName(),
                        RequestAttributes.SCOPE_REQUEST);
        return (token instanceof CsrfToken csrf) ? "<input type=\"hidden\" name=\""
                + HtmlUtils.htmlEscape(csrf.getParameterName()) + "\" value=\""
                + HtmlUtils.htmlEscape(csrf.getToken()) + "\">" : "";
    }
}
