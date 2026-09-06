package dev.springdrop.kernel.field.formatter.types;

import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

/**
 * A number keeping two decimal places unless the display says otherwise, for
 * fields holding fractions rather than whole numbers.
 */
@SpringDropPlugin(id = DecimalNumberFormatter.ID, type = FieldFormatter.class)
public class DecimalNumberFormatter extends NumberFormatter {

    public static final String ID = "number_decimal";

    public static final int DEFAULT_DECIMAL_PLACES = 2;

    public DecimalNumberFormatter() {
        super(ID, DEFAULT_DECIMAL_PLACES);
    }
}
