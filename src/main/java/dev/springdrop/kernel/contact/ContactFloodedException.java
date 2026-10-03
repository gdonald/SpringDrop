package dev.springdrop.kernel.contact;

/** Someone has sent as many messages as the site takes from them for now. */
public class ContactFloodedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ContactFloodedException(String message) {
        super(message);
    }
}
