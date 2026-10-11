package dev.springdrop.kernel.rest;

/**
 * A format REST reads and writes entities in, a plugin of this type whose id
 * is the format's name, as a request's {@code _format} parameter gives it.
 */
public interface RestSerializer {

    /** The media type the format is sent as. */
    String mediaType();

    String serialize(EntityRepresentation entity);

    /** The entity a request body describes. A body that is not the format throws {@link MalformedBodyException}. */
    EntityRepresentation deserialize(String body);

    /** A request body the serializer cannot read. */
    class MalformedBodyException extends IllegalArgumentException {

        private static final long serialVersionUID = 1L;

        public MalformedBodyException(String message) {
            super(message);
        }
    }
}
