package dev.springdrop.support;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.security.ActionLinkTokenService;
import dev.springdrop.kernel.state.StateService;
import java.util.Base64;
import java.util.Optional;

/**
 * An {@link ActionLinkTokenService} signing with a fixed key, for tests that
 * need tokens without a database behind the state store.
 */
public final class TestActionLinkTokens {

    private static final String KEY =
            Base64.getEncoder().encodeToString("a-fixed-signing-key-for-tests".getBytes());

    private TestActionLinkTokens() {
    }

    public static ActionLinkTokenService service() {
        StateService stateService = mock(StateService.class);
        when(stateService.get(anyString(), anyString(), eq(String.class))).thenReturn(Optional.of(KEY));
        return new ActionLinkTokenService(stateService);
    }
}
