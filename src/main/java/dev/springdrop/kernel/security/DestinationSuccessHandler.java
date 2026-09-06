package dev.springdrop.kernel.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

/**
 * Sends someone back where they were headed after they sign in. The destination
 * comes from the sign-in form, so it is caller-supplied and passes through
 * {@link RedirectSafety} before it is used.
 */
public class DestinationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String DESTINATION = "destination";

    private static final String HOME = "/";

    private final RedirectSafety redirectSafety;

    public DestinationSuccessHandler(RedirectSafety redirectSafety) {
        this.redirectSafety = redirectSafety;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {

        getRedirectStrategy().sendRedirect(request, response,
                redirectSafety.destinationOr(request.getParameter(DESTINATION), HOME));
    }
}
