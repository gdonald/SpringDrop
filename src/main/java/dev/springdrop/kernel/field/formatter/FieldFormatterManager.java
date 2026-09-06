package dev.springdrop.kernel.field.formatter;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders a field for reading: its label, then each of its values through the
 * formatter the display names, or the one the field type gets by default. A
 * field holding several values renders them as a list, so their order is kept.
 */
@Component
public class FieldFormatterManager {

    public static final String FORMATTER_SETTING = "formatter";

    private final FieldConfigManager fieldConfigManager;
    private final PluginRegistry pluginRegistry;

    public FieldFormatterManager(FieldConfigManager fieldConfigManager, PluginRegistry pluginRegistry) {
        this.fieldConfigManager = fieldConfigManager;
        this.pluginRegistry = pluginRegistry;
    }

    public FormatterContext context(String entityTypeId, String bundle, String fieldName) {
        return context(entityTypeId, bundle, fieldName, Map.of());
    }

    public FormatterContext context(
            String entityTypeId, String bundle, String fieldName, Map<String, Object> settings) {

        FieldStorageConfig storage = fieldConfigManager.findStorage(entityTypeId, fieldName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No field storage '" + fieldName + "' on entity type '" + entityTypeId + "'"));
        FieldInstanceConfig instance = fieldConfigManager.findInstance(entityTypeId, bundle, fieldName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Field '" + fieldName + "' is not on bundle '" + bundle + "'"));
        return new FormatterContext(storage, instance, settings);
    }

    /** The formatter rendering this field: the one named in settings, else the type's default. */
    public FieldFormatter formatter(FormatterContext context) {
        Object named = context.settings().get(FORMATTER_SETTING);
        String formatterId = (named == null)
                ? fieldConfigManager.fieldType(context.storage().type()).defaultFormatter()
                : named.toString();
        return pluginRegistry.managerFor(FieldFormatter.class).get(formatterId);
    }

    /** The field as it is read: nothing at all when it holds no values. */
    public String render(FormatterContext context, Object value) {
        List<Object> values = valuesOf(value);
        if (values.isEmpty()) {
            return "";
        }

        FieldFormatter formatter = formatter(context);
        StringBuilder markup = new StringBuilder("<div class=\"field field-" + context.fieldName() + "\">");
        if (!context.label().isEmpty()) {
            markup.append("<div class=\"field-label fw-semibold\">")
                    .append(HtmlUtils.htmlEscape(context.label()))
                    .append("</div>");
        }
        if (values.size() == 1) {
            markup.append("<div class=\"field-item\">")
                    .append(formatter.render(context, values.getFirst()))
                    .append("</div>");
        } else {
            markup.append("<ul class=\"field-items list-unstyled mb-0\">");
            values.forEach(item -> markup.append("<li class=\"field-item\">")
                    .append(formatter.render(context, item))
                    .append("</li>"));
            markup.append("</ul>");
        }
        return markup.append("</div>").toString();
    }

    private static List<Object> valuesOf(Object value) {
        if (value == null) {
            return List.of();
        }
        return (value instanceof List<?> list) ? List.copyOf(list) : List.of(value);
    }
}
