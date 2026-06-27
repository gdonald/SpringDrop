package dev.springdrop.kernel.event;

/**
 * Base for "alter" events, the SpringDrop replacement for Drupal's alter hooks.
 * The subject is a mutable object (a form, a query's conditions, a render array)
 * that listeners modify in place before the caller uses it. Concrete alter events
 * extend this as their owning subsystem lands.
 */
public abstract class AlterEvent<T> {

    private final T subject;

    protected AlterEvent(T subject) {
        this.subject = subject;
    }

    public T subject() {
        return subject;
    }
}
