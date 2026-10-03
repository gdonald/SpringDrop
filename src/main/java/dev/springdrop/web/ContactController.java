package dev.springdrop.web;

import dev.springdrop.kernel.access.AccessHelper;
import dev.springdrop.kernel.contact.ContactEntityType;
import dev.springdrop.kernel.contact.ContactFloodedException;
import dev.springdrop.kernel.contact.ContactForm;
import dev.springdrop.kernel.contact.ContactFormManager;
import dev.springdrop.kernel.contact.ContactPermissions;
import dev.springdrop.kernel.contact.ContactSender;
import dev.springdrop.kernel.contact.ContactService;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.field.FieldConstraintProvider;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.web.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Sending messages: through the site's contact forms, and to one person
 * through their personal contact form while they accept messages. Someone
 * signed in sends as their account. Someone not signed in gives a name and an
 * address. A person decides whether they accept personal messages on their
 * contact settings.
 */
@Controller
public class ContactController {

    public static final String PATH = "/contact";

    public static final String SETTINGS_PATH = "/user/contact-settings";

    public static final String NAME = "name";

    public static final String MAIL = "mail";

    public static final String SUBJECT = "subject";

    public static final String MESSAGE = "message";

    public static final String COPY = "copy";

    public static final String ACCEPTS = "contact";

    static final int NAME_MAX_LENGTH = 60;

    static final int SUBJECT_MAX_LENGTH = 100;

    static final int MESSAGE_MAX_LENGTH = 65_535;

    private final ContactFormManager forms;
    private final ContactService contact;
    private final UserAccountService accounts;
    private final EntityCrudService entities;
    private final AccessHelper access;
    private final FormDisplayManager formDisplays;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public ContactController(
            ContactFormManager forms,
            ContactService contact,
            UserAccountService accounts,
            EntityCrudService entities,
            AccessHelper access,
            FormDisplayManager formDisplays,
            FormBuilder formBuilder,
            FormRenderer renderer) {
        this.forms = forms;
        this.contact = contact;
        this.accounts = accounts;
        this.entities = entities;
        this.access = access;
        this.formDisplays = formDisplays;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String personalPath(long accountId) {
        return "/user/" + accountId + "/contact";
    }

    /** The site's chosen contact form. */
    @GetMapping(PATH)
    public String defaultForm(Model model) {
        ContactForm form = forms.defaultForm().orElseThrow(() -> new EntityNotFoundException("contact form", "default"));
        return formPage(form.label(), PATH + "/" + form.id(), messageForm(form, Map.of()), Map.of(), model);
    }

    @GetMapping(PATH + "/{form}")
    public String siteWideForm(@PathVariable String form, Model model) {
        ContactForm contactForm = siteWide(form);
        return formPage(contactForm.label(), PATH + "/" + form, messageForm(contactForm, Map.of()), Map.of(), model);
    }

    @PostMapping(PATH + "/{form}")
    public String send(@PathVariable String form, @RequestParam Map<String, String> submitted,
            HttpServletRequest request, Model model) {
        ContactForm contactForm = siteWide(form);
        FormElement tree = messageForm(contactForm, submitted);
        return sent(contactForm, tree, submitted, request, PATH + "/" + form, contactForm.label(), model,
                (sender, fields) -> contact.send(contactForm, subjectOf(submitted), messageOf(submitted),
                        copyOf(submitted), fields, sender, unlimited()),
                contactForm.redirect().isBlank() ? HomeController.PATH : contactForm.redirect());
    }

    @GetMapping("/user/{id}/contact")
    public String personalForm(@PathVariable long id, Model model) {
        UserAccount recipient = acceptingRecipient(id);
        ContactForm personal = personal();
        return formPage("Contact " + recipient.name(), personalPath(id), messageForm(personal, Map.of()), Map.of(),
                model);
    }

    @PostMapping("/user/{id}/contact")
    public String sendPersonal(@PathVariable long id, @RequestParam Map<String, String> submitted,
            HttpServletRequest request, Model model) {
        UserAccount recipient = acceptingRecipient(id);
        ContactForm personal = personal();
        FormElement tree = messageForm(personal, submitted);
        return sent(personal, tree, submitted, request, personalPath(id), "Contact " + recipient.name(), model,
                (sender, fields) -> contact.sendPersonal(personal, recipient, subjectOf(submitted),
                        messageOf(submitted), copyOf(submitted), fields, sender, unlimited()),
                HomeController.PATH);
    }

    @GetMapping(SETTINGS_PATH)
    public String settingsForm(Model model) {
        EntityData account = ownAccount();
        return settingsPage(accepts(account), model);
    }

    @PostMapping(SETTINGS_PATH)
    public String saveSettings(@RequestParam Map<String, String> submitted) {
        EntityData account = ownAccount();
        Map<String, Object> values = new LinkedHashMap<>(account.fields());
        values.put(UserEntityType.CONTACT, submitted.containsKey(ACCEPTS));
        entities.save(account.withFields(values));
        return "redirect:" + SETTINGS_PATH;
    }

    /** What sends a checked message, given who sent it and the field values it carries. */
    @FunctionalInterface
    private interface Sending {

        EntityData send(ContactSender sender, Map<String, Object> fields);
    }

    /**
     * Sends the message once the form's rules and the message's own constraints
     * pass and the sender may still send, then sends them on. Otherwise the
     * form comes back with what is wrong.
     */
    private String sent(ContactForm form, FormElement tree, Map<String, String> submitted,
            HttpServletRequest request, String action, String title, Model model, Sending sending,
            String redirect) {
        Map<String, Object> values = new LinkedHashMap<>(submitted);
        FormState state = formBuilder.validate(tree, values);
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        Map<String, Object> fields = formDisplays.extract(ContactEntityType.ID, form.id(),
                FormDisplayConfig.DEFAULT_MODE, values);
        try {
            sending.send(sender(submitted, request), fields);
        } catch (ContactFloodedException flooded) {
            return formPage(title, action, tree, Map.of(MESSAGE, flooded.getMessage()), model);
        } catch (EntityValidationException invalid) {
            Map<String, String> errors = new LinkedHashMap<>();
            invalid.violations().forEach(violation -> errors.putIfAbsent(
                    violation.propertyPath().substring(FieldConstraintProvider.FIELD_PATH_PREFIX.length()),
                    violation.message()));
            return formPage(title, action, tree, errors, model);
        }
        return "redirect:" + redirect;
    }

    /** The account signed in, or the name and address someone not signed in gave. */
    private ContactSender sender(Map<String, String> submitted, HttpServletRequest request) {
        long accountId = CurrentAccount.id();
        if (accountId == UserAccount.ANONYMOUS_ID) {
            return new ContactSender(submitted.getOrDefault(NAME, "").strip(),
                    submitted.getOrDefault(MAIL, "").strip(), accountId, request.getRemoteAddr());
        }
        UserAccount account = accounts.find(accountId).orElseThrow();
        return new ContactSender(account.name(), account.mail(), accountId, request.getRemoteAddr());
    }

    /**
     * The message form: a name and address for someone not signed in, the
     * subject and message, the fields the form's messages carry, and a copy for
     * someone signed in.
     */
    private FormElement messageForm(ContactForm form, Map<String, String> submitted) {
        FormElement tree = FormElement.of(ElementType.CONTAINER, "contact-message");
        boolean signedIn = CurrentAccount.id() != UserAccount.ANONYMOUS_ID;
        if (!signedIn) {
            tree.child(FormElement.of(ElementType.TEXTFIELD, NAME).label("Your name").markRequired()
                            .value(submitted.getOrDefault(NAME, ""))
                            .rule(ValidationRule.maxLength(NAME_MAX_LENGTH)))
                    .child(FormElement.of(ElementType.TEXTFIELD, MAIL).label("Your email address").markRequired()
                            .value(submitted.getOrDefault(MAIL, ""))
                            .rule(ValidationRule.maxLength(254))
                            .rule(ValidationRule.email()));
        }
        tree.child(FormElement.of(ElementType.TEXTFIELD, SUBJECT).label("Subject").markRequired()
                        .value(submitted.getOrDefault(SUBJECT, ""))
                        .rule(ValidationRule.maxLength(SUBJECT_MAX_LENGTH)))
                .child(FormElement.of(ElementType.TEXTAREA, MESSAGE).label("Message").markRequired()
                        .value(submitted.getOrDefault(MESSAGE, ""))
                        .rule(ValidationRule.maxLength(MESSAGE_MAX_LENGTH)))
                .child(formDisplays.buildContainer(ContactEntityType.ID, form.id(), FormDisplayConfig.DEFAULT_MODE,
                        Map.of()));
        if (signedIn) {
            tree.child(FormElement.of(ElementType.CHECKBOX, COPY).label("Send yourself a copy")
                    .value(submitted.containsKey(COPY)));
        }
        return tree.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "send").label("Send message")));
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors,
            Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private String settingsPage(boolean accepts, Model model) {
        FormElement tree = FormElement.of(ElementType.CONTAINER, "contact-settings")
                .child(FormElement.of(ElementType.CHECKBOX, ACCEPTS).label("Personal contact form")
                        .description("Let other people send you messages through your contact form. "
                                + "Your email address is not shown to them.")
                        .value(accepts))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save")));
        model.addAttribute("title", "Contact settings");
        model.addAttribute("description", "");
        model.addAttribute("action", SETTINGS_PATH);
        model.addAttribute("formMarkup", renderer.render(tree));
        return "admin/block-form";
    }

    private ContactForm siteWide(String id) {
        return forms.find(id).filter(form -> !form.personal())
                .orElseThrow(() -> new EntityNotFoundException("contact form", id));
    }

    private ContactForm personal() {
        return forms.find(ContactForm.PERSONAL).orElseThrow(() -> new EntityNotFoundException("contact form",
                ContactForm.PERSONAL));
    }

    /** An open account that accepts personal messages. */
    private UserAccount acceptingRecipient(long id) {
        EntityData account = accounts.load(id)
                .filter(found -> ((Number) found.id()).longValue() != UserAccount.ANONYMOUS_ID)
                .orElseThrow(() -> new EntityNotFoundException("account", String.valueOf(id)));
        UserAccount recipient = accounts.find(id).orElseThrow();
        if (!recipient.active() || !accepts(account)) {
            throw new AccessDeniedException(recipient.name() + " does not accept messages.");
        }
        return recipient;
    }

    /** Whether the account accepts personal messages, which one that never said does. */
    private static boolean accepts(EntityData account) {
        return !Boolean.FALSE.equals(account.fields().get(UserEntityType.CONTACT));
    }

    private EntityData ownAccount() {
        long id = CurrentAccount.id();
        if (id == UserAccount.ANONYMOUS_ID) {
            throw new AccessDeniedException("Sign in to change your contact settings.");
        }
        return accounts.load(id).orElseThrow(() -> new EntityNotFoundException("account", String.valueOf(id)));
    }

    private boolean unlimited() {
        return access.has(ContactPermissions.ADMINISTER);
    }

    private static String subjectOf(Map<String, String> submitted) {
        return submitted.getOrDefault(SUBJECT, "").strip();
    }

    private static String messageOf(Map<String, String> submitted) {
        return submitted.getOrDefault(MESSAGE, "");
    }

    private static boolean copyOf(Map<String, String> submitted) {
        return CurrentAccount.id() != UserAccount.ANONYMOUS_ID && submitted.containsKey(COPY);
    }
}
