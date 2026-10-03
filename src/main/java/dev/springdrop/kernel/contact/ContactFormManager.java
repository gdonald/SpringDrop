package dev.springdrop.kernel.contact;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** The site's contact forms, each stored as the config entity {@code contact_form.<id>}. */
@Component
public class ContactFormManager {

    private final ConfigStore configStore;

    public ContactFormManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public static String configName(String id) {
        return ContactEntityType.FORM_ID + "." + id;
    }

    /** Adds the personal form as the application starts, when the site has none. */
    @EventListener(ApplicationReadyEvent.class)
    public void installPersonal() {
        if (find(ContactForm.PERSONAL).isEmpty()) {
            save(ContactForm.of(ContactForm.PERSONAL, "Personal contact form", List.of()));
        }
    }

    /**
     * Saves a form. A form saved as the one {@code /contact} shows stops any
     * other form being it.
     */
    public void save(ContactForm form) {
        if (form.selected()) {
            all().stream()
                    .filter(other -> other.selected() && !other.id().equals(form.id()))
                    .forEach(other -> configStore.save(configName(other.id()), new ContactForm(other.id(),
                            other.label(), other.recipients(), other.reply(), other.redirect(), other.weight(),
                            false)));
        }
        configStore.save(configName(form.id()), form);
    }

    public Optional<ContactForm> find(String id) {
        return Optional.ofNullable(configStore.read(configName(id), ContactForm.class, null));
    }

    public void delete(String id) {
        configStore.delete(configName(id));
    }

    /** Every form, lightest first and then by label. */
    public List<ContactForm> all() {
        List<ContactForm> forms = new ArrayList<>();
        for (String name : configStore.listNames(ContactEntityType.FORM_ID)) {
            find(name.substring(ContactEntityType.FORM_ID.length() + 1)).ifPresent(forms::add);
        }
        return forms.stream()
                .sorted(Comparator.comparingInt(ContactForm::weight).thenComparing(ContactForm::label))
                .toList();
    }

    /** The form {@code /contact} shows: the selected one, else the first site-wide form. */
    public Optional<ContactForm> defaultForm() {
        List<ContactForm> siteWide = all().stream().filter(form -> !form.personal()).toList();
        return siteWide.stream().filter(ContactForm::selected).findFirst()
                .or(() -> siteWide.stream().findFirst());
    }
}
