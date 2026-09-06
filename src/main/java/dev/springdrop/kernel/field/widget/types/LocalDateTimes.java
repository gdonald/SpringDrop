package dev.springdrop.kernel.field.widget.types;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Moves a moment between how it is stored and how a browser shows it. Storage
 * keeps an instant with its offset; a {@code datetime-local} input holds a wall
 * clock reading with none, so the two are converted through the timezone the
 * reader is in.
 */
interface LocalDateTimes {

    /** What a datetime-local input holds: minutes, no seconds, no offset. */
    DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    /** The stored instant as the wall clock reading of the given zone. */
    static String toInput(Object stored, ZoneId zone) {
        if (stored == null) {
            return "";
        }
        try {
            return OffsetDateTime.parse(stored.toString()).atZoneSameInstant(zone).format(INPUT_FORMAT);
        } catch (DateTimeParseException e) {
            return stored.toString();
        }
    }

    /** The wall clock reading as a stored instant, read in the given zone. */
    static String toStored(String entered, ZoneId zone) {
        try {
            return LocalDateTime.parse(entered).atZone(zone).toOffsetDateTime().toString();
        } catch (DateTimeParseException e) {
            return entered;
        }
    }
}
