package dev.springdrop.kernel.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

class SiteErrorViewResolverTest {

    private final ConfigStore configStore = Mockito.mock(ConfigStore.class);

    private final SiteErrorViewResolver resolver = new SiteErrorViewResolver(configStore);

    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");

    private void configured(ErrorPagesConfig config) {
        when(configStore.read(eq(ErrorPagesConfig.CONFIG_NAME), any(), any())).thenReturn(config);
    }

    private ModelAndView resolve(HttpStatus status) {
        return resolver.resolveErrorView(request, status, Map.of());
    }

    @Test
    void forwardsToTheConfiguredNotFoundPath() {
        configured(new ErrorPagesConfig("", "/site-404"));

        assertThat(resolve(HttpStatus.NOT_FOUND).getViewName()).isEqualTo("forward:/site-404");
    }

    @Test
    void forwardsToTheConfiguredForbiddenPath() {
        configured(new ErrorPagesConfig("/site-403", ""));

        assertThat(resolve(HttpStatus.FORBIDDEN).getViewName()).isEqualTo("forward:/site-403");
    }

    @Test
    void leavesTheDefaultPageInPlaceWhenNoPathIsConfigured() {
        configured(ErrorPagesConfig.DEFAULTS);

        assertThat(resolve(HttpStatus.NOT_FOUND)).isNull();
    }

    @Test
    void leavesTheDefaultPageInPlaceWhenTheConfiguredPathIsNull() {
        configured(new ErrorPagesConfig(null, null));

        assertThat(resolve(HttpStatus.FORBIDDEN)).isNull();
    }

    @Test
    void leavesOtherStatusesToTheDefaultPage() {
        assertThat(resolve(HttpStatus.INTERNAL_SERVER_ERROR)).isNull();
        Mockito.verifyNoInteractions(configStore);
    }

    @Test
    void forwardsAtMostOncePerRequest() {
        configured(new ErrorPagesConfig("", "/site-404"));

        resolve(HttpStatus.NOT_FOUND);

        assertThat(resolve(HttpStatus.NOT_FOUND)).isNull();
    }
}
