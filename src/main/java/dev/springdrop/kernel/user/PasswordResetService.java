package dev.springdrop.kernel.user;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.mail.MailManager;
import dev.springdrop.kernel.token.TokenContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Getting back into an account whose password is lost. A link is sent to the
 * address on the account, works once, and lapses on its own, so an old message
 * left in a mailbox is of no use.
 */
@Component
public class PasswordResetService {

    public static final String RESET_SUBJECT = "Signing back in to [site:name]";

    public static final String RESET_BODY =
            "Use this link once to sign in and set a new password: ";

    public static final String RESET_PATH = "/user/reset/";

    private final UserAccountService accounts;
    private final OneTimeLinkService oneTimeLinks;
    private final MailManager mail;
    private final EntityCrudService entities;
    private final PasswordEncoder passwordEncoder;
    private final FloodService flood;

    public PasswordResetService(
            UserAccountService accounts,
            OneTimeLinkService oneTimeLinks,
            MailManager mail,
            EntityCrudService entities,
            PasswordEncoder passwordEncoder,
            FloodService flood) {
        this.accounts = accounts;
        this.oneTimeLinks = oneTimeLinks;
        this.mail = mail;
        this.entities = entities;
        this.passwordEncoder = passwordEncoder;
        this.flood = flood;
    }

    /**
     * Sends a one-time link to the account of this name. A name nobody holds is
     * answered the same way from the outside, so the form cannot be used to find
     * out who has an account, and asking too often stops sending anything at
     * all, so the form cannot be used to bury someone in mail.
     */
    public Optional<String> requestReset(String name) {
        if (!flood.isAllowed(FloodSettings.PASSWORD_RESET, name,
                FloodSettings.RESET_THRESHOLD, FloodSettings.RESET_WINDOW)) {
            return Optional.empty();
        }
        flood.register(FloodSettings.PASSWORD_RESET, name, FloodSettings.RESET_WINDOW);

        return accounts.findByName(name).map(account -> {
            String token = oneTimeLinks.issue(account.id(), OneTimeLinkService.RESET_PASSWORD);
            mail.send(account.mail(), RESET_SUBJECT, RESET_BODY + RESET_PATH + token,
                    TokenContext.of(Map.of()));
            return token;
        });
    }

    /** The account a reset link stands for, spending the link. */
    public Optional<UserAccount> claim(String token) {
        return oneTimeLinks.consume(token, OneTimeLinkService.RESET_PASSWORD)
                .flatMap(accountId -> accounts.find(accountId));
    }

    public void setPassword(long accountId, String password) {
        entities.load(UserEntityType.ID, accountId).ifPresent(stored -> {
            Map<String, Object> values = new LinkedHashMap<>(stored.fields());
            values.put(UserEntityType.PASSWORD_HASH, passwordEncoder.encode(password));
            entities.save(stored.withFields(values));
        });
    }
}
