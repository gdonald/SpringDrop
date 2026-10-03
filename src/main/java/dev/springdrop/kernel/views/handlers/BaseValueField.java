package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * A value the entity holds itself rather than in a configured field: its
 * {@code id}, {@code label}, {@code bundle}, or a base field such as
 * {@code status} or {@code created}. True and false read as the
 * {@code true_label} and {@code false_label} settings, Yes and No by default,
 * and a moment as its date and time.
 */
@SpringDropPlugin(id = BaseValueField.ID, type = FieldHandler.class)
public class BaseValueField implements FieldHandler {

    public static final String ID = "base_value";

    public static final String TRUE_LABEL = "true_label";

    public static final String FALSE_LABEL = "false_label";

    private static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Value";
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        return row.entity(config.relationship()).map(entity -> HtmlUtils.htmlEscape(text(entity, config)))
                .orElse("");
    }

    static String text(EntityData entity, HandlerConfig config) {
        String property = config.property();
        Object value = switch (property) {
            case "id" -> entity.id();
            case "label" -> entity.label();
            case "bundle" -> entity.bundle();
            default -> entity.fields().get(property);
        };
        if (value instanceof Boolean flag) {
            return String.valueOf(flag ? config.setting(TRUE_LABEL, "Yes") : config.setting(FALSE_LABEL, "No"));
        }
        if (value instanceof OffsetDateTime moment) {
            return MOMENT.format(moment);
        }
        return (value == null) ? "" : value.toString();
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings),
                Settings.text(prefix, TRUE_LABEL, "Shown for true", settings),
                Settings.text(prefix, FALSE_LABEL, "Shown for false", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LABEL, Settings.submitted(prefix, LABEL, submitted));
        String yes = Settings.submitted(prefix, TRUE_LABEL, submitted);
        String no = Settings.submitted(prefix, FALSE_LABEL, submitted);
        values.put(TRUE_LABEL, yes.isEmpty() ? "Yes" : yes);
        values.put(FALSE_LABEL, no.isEmpty() ? "No" : no);
        return values;
    }
}
