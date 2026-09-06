package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

/**
 * A number with the prefix, suffix, and separators the display asks for, such as
 * a price shown as {@code $1,234.50}. Whole numbers go through
 * {@code number_integer} and fractional ones through {@code number_decimal},
 * which differ only in how many decimal places they keep by default.
 */
@SpringDropPlugin(id = NumberFormatter.INTEGER_ID, type = FieldFormatter.class)
public class NumberFormatter implements FieldFormatter {

    public static final String INTEGER_ID = "number_integer";

    public static final String PREFIX = "prefix";

    public static final String SUFFIX = "suffix";

    public static final String THOUSANDS_SEPARATOR = "thousands_separator";

    public static final String DECIMAL_SEPARATOR = "decimal_separator";

    public static final String DECIMAL_PLACES = "decimal_places";

    private static final char NO_SEPARATOR = ' ';

    private final String id;
    private final int defaultDecimalPlaces;

    public NumberFormatter() {
        this(INTEGER_ID, 0);
    }

    NumberFormatter(String id, int defaultDecimalPlaces) {
        this.id = id;
        this.defaultDecimalPlaces = defaultDecimalPlaces;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        int places = ((Number) context.setting(DECIMAL_PLACES, defaultDecimalPlaces)).intValue();

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ENGLISH);
        symbols.setGroupingSeparator(separator(context, THOUSANDS_SEPARATOR, ","));
        symbols.setDecimalSeparator(separator(context, DECIMAL_SEPARATOR, "."));

        DecimalFormat format = new DecimalFormat(pattern(places), symbols);
        format.setRoundingMode(RoundingMode.HALF_UP);
        String number = format.format(new BigDecimal(String.valueOf(value)));

        return HtmlUtils.htmlEscape(context.text(PREFIX, "") + number + context.text(SUFFIX, ""));
    }

    private static String pattern(int decimalPlaces) {
        return (decimalPlaces == 0) ? "#,##0" : "#,##0." + "0".repeat(decimalPlaces);
    }

    /** A separator the display leaves empty means the digits run together. */
    private static char separator(FormatterContext context, String setting, String fallback) {
        String separator = context.text(setting, fallback);
        return separator.isEmpty() ? NO_SEPARATOR : separator.charAt(0);
    }
}
