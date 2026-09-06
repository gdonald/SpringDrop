package dev.springdrop.kernel.user;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.mail.MailManager;
import dev.springdrop.kernel.token.TokenContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Signing up, and what happens next. A site that approves accounts by hand
 * leaves a new one blocked until someone lets it in; a site that checks
 * addresses sends a one-time link and waits to be told the address is real.
 */
@Component
public class RegistrationService {

    public static final String WELCOME_SUBJECT = "Your account at [site:name]";

    public static final String WELCOME_BODY =
            "Welcome to [site:name]. Your account is ready to use.";

    public static final String APPROVAL_SUBJECT = "Your account at [site:name] is waiting";

    public static final String APPROVAL_BODY =
            "Thank you for signing up at [site:name]. Someone will let you in shortly.";

    public static final String APPROVED_SUBJECT = "Your account at [site:name] is open";

    public static final String APPROVED_BODY = "You can now sign in at [site:name].";

    private final UserAccountService accounts;
    private final ConfigStore configStore;
    private final PasswordEncoder passwordEncoder;
    private final OneTimeLinkService oneTimeLinks;
    private final MailManager mail;
    private final EntityCrudService entities;

    public RegistrationService(
            UserAccountService accounts,
            ConfigStore configStore,
            PasswordEncoder passwordEncoder,
            OneTimeLinkService oneTimeLinks,
            MailManager mail,
            EntityCrudService entities) {
        this.accounts = accounts;
        this.configStore = configStore;
        this.passwordEncoder = passwordEncoder;
        this.oneTimeLinks = oneTimeLinks;
        this.mail = mail;
        this.entities = entities;
    }

    public UserSettings settings() {
        return configStore.read(UserSettings.CONFIG_NAME, UserSettings.class, UserSettings.DEFAULTS);
    }

    /** Signs someone up, as far as the site's settings let them go on their own. */
    public UserAccount register(String name, String mailAddress, String password) {
        UserSettings settings = settings();
        if (!settings.allowsSelfRegistration()) {
            throw new IllegalStateException("This site does not take applications for accounts");
        }

        UserAccount account = accounts.create(name, mailAddress, passwordEncoder.encode(password));
        if (settings.needsApproval()) {
            accounts.block(account.id());
            send(mailAddress, APPROVAL_SUBJECT, APPROVAL_BODY);
            return accounts.find(account.id()).orElseThrow();
        }

        if (settings.requireEmailVerification()) {
            accounts.block(account.id());
            String token = oneTimeLinks.issue(account.id(), OneTimeLinkService.VERIFY_MAIL);
            send(mailAddress, WELCOME_SUBJECT, WELCOME_BODY + " Confirm your address: /user/verify/" + token);
            return accounts.find(account.id()).orElseThrow();
        }

        send(mailAddress, WELCOME_SUBJECT, WELCOME_BODY);
        return account;
    }

    /** Lets a waiting account in, and tells the person so. */
    public void approve(long accountId) {
        activate(accountId);
        accounts.find(accountId).ifPresent(account -> send(account.mail(), APPROVED_SUBJECT, APPROVED_BODY));
    }

    /** Confirms the address a one-time link was sent to, opening the account. */
    public boolean verifyMail(String token) {
        return oneTimeLinks.consume(token, OneTimeLinkService.VERIFY_MAIL)
                .map(accountId -> {
                    activate(accountId);
                    return true;
                })
                .orElse(false);
    }

    private void activate(long accountId) {
        entities.load(UserEntityType.ID, accountId).ifPresent(stored -> {
            Map<String, Object> values = new LinkedHashMap<>(stored.fields());
            values.put("status", true);
            entities.save(stored.withFields(values));
        });
    }

    private void send(String to, String subject, String body) {
        mail.send(to, subject, body, TokenContext.of(Map.of()));
    }
}
