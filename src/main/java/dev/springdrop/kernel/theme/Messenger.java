package dev.springdrop.kernel.theme;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Status messages for the person making the request, such as "Saved.", kept
 * in their session until a page shows them. An action adds a message and
 * redirects, and the page the redirect lands on shows it once.
 */
@Component
public class Messenger {

    static final String ATTRIBUTE = Messenger.class.getName();

    public void add(StatusMessage message) {
        HttpSession session = session(true);
        if (session != null) {
            List<StatusMessage> waiting = new ArrayList<>(waiting(session));
            waiting.add(message);
            session.setAttribute(ATTRIBUTE, waiting);
        }
    }

    public void success(String text) {
        add(StatusMessage.success(text));
    }

    /** The messages waiting to be shown, which are then forgotten. */
    public List<StatusMessage> drain() {
        HttpSession session = session(false);
        if (session == null) {
            return List.of();
        }
        List<StatusMessage> waiting = waiting(session);
        session.removeAttribute(ATTRIBUTE);
        return waiting;
    }

    /** The messages for a template, drained when it first asks for them. */
    public View view() {
        return new View(this);
    }

    /** The messages a page draws, taken from the session only when the page draws them. */
    public static final class View {

        private final Messenger messenger;
        private List<StatusMessage> drained;

        View(Messenger messenger) {
            this.messenger = messenger;
        }

        public List<StatusMessage> all() {
            if (drained == null) {
                drained = messenger.drain();
            }
            return drained;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<StatusMessage> waiting(HttpSession session) {
        Object stored = session.getAttribute(ATTRIBUTE);
        return stored instanceof List<?> list ? (List<StatusMessage>) list : List.of();
    }

    private static HttpSession session(boolean create) {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request
                ? request.getRequest().getSession(create) : null;
    }
}
