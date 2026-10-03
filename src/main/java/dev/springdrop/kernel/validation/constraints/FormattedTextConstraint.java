package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Requires a formatted text value to be written in a format the site has and
 * the person saving it may use, to stay within the {@code max_length} option
 * when it has one, and to carry a summary when {@code required_summary} is set.
 * A save made outside a request, such as by an install step, is not asked who
 * is saving. A missing value passes.
 */
@SpringDropPlugin(id = FormattedTextConstraint.ID, type = Constraint.class)
public class FormattedTextConstraint implements Constraint {

    public static final String ID = "formatted_text";

    private final TextFormatManager formats;

    public FormattedTextConstraint(TextFormatManager formats) {
        this.formats = formats;
    }

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Map<?, ?>)) {
            return Optional.of("This value is not formatted text.");
        }
        String text = FormattedText.part(value, FormattedText.VALUE);
        Object limit = options.get(FormattedText.MAX_LENGTH);
        if (limit != null && text.length() > Integer.parseInt(limit.toString())) {
            return Optional.of("This value is longer than " + limit + " characters.");
        }
        if (Boolean.TRUE.equals(options.get(FormattedText.REQUIRED_SUMMARY))
                && FormattedText.part(value, FormattedText.SUMMARY).isBlank()) {
            return Optional.of("A summary is required.");
        }
        Authentication saving = SecurityContextHolder.getContext().getAuthentication();
        return formats.find(FormattedText.part(value, FormattedText.FORMAT))
                .filter(format -> saving == null || formats.mayUse(format, saving))
                .map(format -> Optional.<String>empty())
                .orElse(Optional.of("Choose a text format you may use."));
    }
}
