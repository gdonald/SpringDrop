package dev.springdrop.kernel.security;

import dev.springdrop.kernel.state.StateService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Signs and validates the tokens that protect state-changing GET links, such as
 * the module enable and entity delete links in the admin UI. The token is an
 * HMAC over the link's path and the requesting user's identity, so a link
 * cannot be triggered from another site and a token minted for one user does
 * not work for another. The signing key is generated once and kept in state, so
 * tokens stay valid across a restart and every instance sharing the database
 * signs alike.
 */
@Component
public class ActionLinkTokenService {

    public static final String TOKEN_PARAMETER = "token";

    static final String STATE_COLLECTION = "system";

    static final String STATE_KEY = "action_link_key";

    private static final String ANONYMOUS_IDENTITY = "anonymous";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final StateService stateService;
    private final String algorithm;

    @Autowired
    public ActionLinkTokenService(StateService stateService) {
        this(stateService, HMAC_ALGORITHM);
    }

    ActionLinkTokenService(StateService stateService, String algorithm) {
        this.stateService = stateService;
        this.algorithm = algorithm;
    }

    public String token(String path) {
        return token(path, identityOf(SecurityContextHolder.getContext().getAuthentication()));
    }

    public String token(String path, String identity) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(signingKey(), algorithm));
            byte[] signature = mac.doFinal((path + " " + identity).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign an action link token", e);
        }
    }

    public boolean isValid(String path, String token, String identity) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                token(path, identity).getBytes(StandardCharsets.UTF_8));
    }

    public String tokenizedPath(String path) {
        return path + "?" + TOKEN_PARAMETER + "=" + token(path);
    }

    public static String identityOf(Authentication authentication) {
        return (authentication == null) ? ANONYMOUS_IDENTITY : authentication.getName();
    }

    private byte[] signingKey() {
        String stored = stateService.get(STATE_COLLECTION, STATE_KEY, String.class)
                .orElseGet(this::generateAndStoreKey);
        return Base64.getDecoder().decode(stored);
    }

    private String generateAndStoreKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        stateService.set(STATE_COLLECTION, STATE_KEY, encoded);
        return encoded;
    }
}
