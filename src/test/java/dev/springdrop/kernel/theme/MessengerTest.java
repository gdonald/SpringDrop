package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;

class MessengerTest {

    @Test
    void outsideARequestThereIsNoSessionToKeepMessagesIn() {
        RequestContextHolder.resetRequestAttributes();
        Messenger messenger = new Messenger();

        messenger.success("Saved.");

        assertThat(messenger.drain()).isEmpty();
        assertThat(messenger.view().all()).isEmpty();
    }
}
