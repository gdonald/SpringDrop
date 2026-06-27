package dev.springdrop.kernel.mail;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/**
 * The default mail backend: it records delivered messages in memory rather than
 * sending them, so local development and tests can inspect outgoing mail. An SMTP
 * backend supersedes it in production.
 */
@Component
public class CollectingMailBackend implements MailBackend {

    private final List<MailMessage> delivered = new CopyOnWriteArrayList<>();

    @Override
    public void deliver(MailMessage message) {
        delivered.add(message);
    }

    public List<MailMessage> delivered() {
        return List.copyOf(delivered);
    }

    public void clear() {
        delivered.clear();
    }
}
