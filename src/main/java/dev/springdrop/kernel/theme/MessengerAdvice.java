package dev.springdrop.kernel.theme;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives every view the waiting status messages as the {@code statusMessages} model attribute. */
@ControllerAdvice
public class MessengerAdvice {

    private final Messenger messenger;

    public MessengerAdvice(Messenger messenger) {
        this.messenger = messenger;
    }

    @ModelAttribute("statusMessages")
    public Messenger.View statusMessages() {
        return messenger.view();
    }
}
