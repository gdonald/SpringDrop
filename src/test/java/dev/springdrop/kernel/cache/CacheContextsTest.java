package dev.springdrop.kernel.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.user.AccountPrincipal;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class CacheContextsTest {

    private final CacheContexts contexts = new CacheContexts();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        LocaleContextHolder.resetLocaleContext();
    }

    private static void request(String uri, String query) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/site" + uri);
        request.setContextPath("/site");
        request.setQueryString(query);
        request.addParameter("page", "2");
        request.addParameter("tag", "a", "b");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void reader() {
        AccountPrincipal edith = new AccountPrincipal(7L, "edith", "", true, List.of("access content"),
                List.of("editor"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(edith, null,
                edith.getAuthorities()));
    }

    @Test
    void requestContextsReadThePathAndQueryArguments() {
        request("/news", "page=2&tag=a&tag=b");

        assertThat(contexts.value(CacheContexts.URL_PATH)).isEqualTo("/news");
        assertThat(contexts.value(CacheContexts.ROUTE)).isEqualTo("/news");
        assertThat(contexts.value(CacheContexts.URL)).isEqualTo("/news?page=2&tag=a&tag=b");
        assertThat(contexts.value(CacheContexts.QUERY_ARGS)).isEqualTo("page=[2]&tag=[a, b]");
        assertThat(contexts.value(CacheContexts.QUERY_ARGS + ":tag")).isEqualTo("a,b");
        assertThat(contexts.value(CacheContexts.QUERY_ARGS + ":missing")).isEmpty();
    }

    @Test
    void aRequestWithoutAQueryStringHasAnEmptyOne() {
        request("/news", null);

        assertThat(contexts.value(CacheContexts.URL)).isEqualTo("/news?");
    }

    @Test
    void userContextsReadTheAccount() {
        reader();

        assertThat(contexts.value(CacheContexts.USER)).isEqualTo("edith");
        assertThat(contexts.value(CacheContexts.USER_ROLES)).isEqualTo("authenticated,editor");
        assertThat(contexts.value(CacheContexts.USER_PERMISSIONS)).isEqualTo("ROLE_editor,access content");
    }

    @Test
    void someoneNotSignedInHasNoNameAndNoPermissions() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThat(contexts.value(CacheContexts.USER)).isEmpty();
        assertThat(contexts.value(CacheContexts.USER_PERMISSIONS)).isEmpty();
        assertThat(contexts.value(CacheContexts.USER_ROLES)).isEqualTo("anonymous");
    }

    @Test
    void withoutAnyoneSignedInTheUserContextsAreEmpty() {
        assertThat(contexts.values(List.of(CacheContexts.USER, CacheContexts.USER_PERMISSIONS)))
                .isEqualTo("{user=, user.permissions=}");
    }

    @Test
    void outsideARequestRequestContextsAreEmptyAndTheLanguageIsTheLocale() {
        LocaleContextHolder.setLocale(Locale.FRANCE);

        assertThat(contexts.values(List.of(CacheContexts.URL, CacheContexts.URL_PATH, CacheContexts.QUERY_ARGS,
                CacheContexts.QUERY_ARGS + ":page", CacheContexts.LANGUAGES)))
                .isEqualTo("{languages=fr-FR, url=, url.path=, url.query_args=, url.query_args:page=}");
    }

    @Test
    void aContextTheSiteLacksIsRefused() {
        assertThatThrownBy(() -> contexts.value("weather")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The site has no cache context weather.");
    }
}
