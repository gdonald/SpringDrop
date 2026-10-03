package dev.springdrop.kernel.contact;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.mail.MailManager;
import dev.springdrop.kernel.mail.MailRequest;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.user.UserAccount;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Sending contact messages. A message is kept as a contact message entity and
 * mailed: a site-wide form's to its recipients, with the sender's address to
 * reply to, and a personal one to the account it is for, with the site's own
 * address to reply to so the sender's stays hidden. The sender gets the form's
 * automatic reply when it has one, and a copy when they asked for one.
 *
 * <p>Each sender may send a handful of messages an hour, counted per account,
 * or per address for someone not signed in, unless they may skip the limit.
 */
@Component
public class ContactService {

    public static final String COPY_NOTE = "This is a copy of the message you sent.";

    private final EntityCrudService entities;
    private final EntityTypeManager entityTypeManager;
    private final MailManager mail;
    private final FloodService flood;
    private final ConfigStore configStore;
    private final Clock clock;

    public ContactService(
            EntityCrudService entities,
            EntityTypeManager entityTypeManager,
            MailManager mail,
            FloodService flood,
            ConfigStore configStore,
            Clock clock) {
        this.entities = entities;
        this.entityTypeManager = entityTypeManager;
        this.mail = mail;
        this.flood = flood;
        this.configStore = configStore;
        this.clock = clock;
    }

    /**
     * Creates the tables contact messages are stored in as the application
     * starts. Creating tables that exist leaves them as they are.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        entityTypeManager.installStorage(ContactEntityType.ID);
    }

    /** Whether the sender may send another message now. */
    public boolean mayStillSend(ContactSender sender) {
        return flood.isAllowed(FloodSettings.CONTACT, floodIdentifier(sender),
                FloodSettings.CONTACT_THRESHOLD, FloodSettings.CONTACT_WINDOW);
    }

    /**
     * Sends a message through a site-wide form: kept, mailed to the form's
     * recipients, answered with the form's automatic reply, and copied to the
     * sender when they asked.
     */
    public EntityData send(ContactForm form, String subject, String text, boolean copy, Map<String, Object> fields,
            ContactSender sender, boolean unlimited) {
        EntityData message = kept(form, subject, text, copy, fields, sender, null, unlimited);
        String mailSubject = "[" + form.label() + "] " + subject;
        String body = sender.name() + " (" + sender.mail() + ") sent a message using the contact form at "
                + site().url() + "/contact/" + form.id() + ".\n\n" + text;
        for (String recipient : form.recipients()) {
            mail.send(MailRequest.literal(recipient, mailSubject, body, sender.mail()));
        }
        if (!form.reply().isBlank()) {
            mail.send(MailRequest.literal(sender.mail(), mailSubject, form.reply(), null));
        }
        if (copy) {
            mail.send(MailRequest.literal(sender.mail(), mailSubject, COPY_NOTE + "\n\n" + body, null));
        }
        return message;
    }

    /**
     * Sends a message to one account through their personal contact form. The
     * mail to them replies to the site, so the sender's address stays hidden.
     */
    public EntityData sendPersonal(ContactForm personal, UserAccount recipient, String subject, String text,
            boolean copy, Map<String, Object> fields, ContactSender sender, boolean unlimited) {
        EntityData message = kept(personal, subject, text, copy, fields, sender, recipient.id(), unlimited);
        SiteInformation site = site();
        String mailSubject = "[" + site.name() + "] " + subject;
        String body = "Hello " + recipient.name() + ",\n\n" + sender.name()
                + " has sent you a message through your contact form at " + site.name() + ".\n\n"
                + "If you do not want to receive such messages, you can turn your contact form off at "
                + site.url() + "/user/contact-settings.\n\nMessage:\n\n" + text;
        mail.send(MailRequest.literal(recipient.mail(), mailSubject, body, site.mail()));
        if (copy) {
            mail.send(MailRequest.literal(sender.mail(), mailSubject, COPY_NOTE + "\n\n" + text, null));
        }
        return message;
    }

    private EntityData kept(ContactForm form, String subject, String text, boolean copy, Map<String, Object> fields,
            ContactSender sender, Long recipient, boolean unlimited) {
        if (!unlimited && !mayStillSend(sender)) {
            throw new ContactFloodedException("You cannot send more than " + FloodSettings.CONTACT_THRESHOLD
                    + " messages in an hour. Try again later.");
        }
        Map<String, Object> values = new LinkedHashMap<>(fields);
        values.put(ContactEntityType.NAME, sender.name());
        values.put(ContactEntityType.MAIL, sender.mail());
        values.put(ContactEntityType.MESSAGE, text);
        values.put(ContactEntityType.COPY, copy);
        values.put(ContactEntityType.IP, sender.ip());
        values.put(BaseFieldDefinition.OWNER, sender.accountId());
        values.put(BaseFieldDefinition.CREATED, OffsetDateTime.now(clock));
        Optional.ofNullable(recipient).ifPresent(id -> values.put(ContactEntityType.RECIPIENT, id));
        EntityData saved = entities.save(EntityData.of(ContactEntityType.ID, null, form.id(), subject, values));
        flood.register(FloodSettings.CONTACT, floodIdentifier(sender), FloodSettings.CONTACT_WINDOW);
        return saved;
    }

    private static String floodIdentifier(ContactSender sender) {
        return (sender.accountId() == UserAccount.ANONYMOUS_ID) ? "ip:" + sender.ip() : "account:" + sender.accountId();
    }

    private SiteInformation site() {
        return configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
    }
}
