package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.block.BlockSettings;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FieldFormatterManager;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.render.Renderable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One field of the entity a page is about, drawn through the formatter its
 * settings name. It shows only on a page about an entity of the bundle the
 * field is on, and shows nothing when that entity holds no value for it. The
 * field's own label is left off, since the block's title can show it.
 */
public class FieldBlock implements BlockPlugin {

    public static final String FORMATTER = FieldFormatterManager.FORMATTER_SETTING;

    public static final String FORMATTER_ELEMENT = SETTINGS_PREFIX + FORMATTER;

    public static final String UNKNOWN_FORMATTER_MESSAGE = "Choose one of the formatters the site has.";

    private final FieldInstanceConfig instance;
    private final FieldFormatterManager formatters;
    private final PluginRegistry plugins;

    public FieldBlock(FieldInstanceConfig instance, FieldFormatterManager formatters, PluginRegistry plugins) {
        this.instance = instance;
        this.formatters = formatters;
        this.plugins = plugins;
    }

    @Override
    public String label() {
        return instance.label();
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return context.routeEntity()
                .filter(this::isOfThisBundle)
                .map(entity -> formatters.render(formatterContext(settings).withEntity(entity),
                        entity.fields().get(instance.fieldName())))
                .filter(markup -> !markup.isEmpty())
                .map(markup -> Renderable.of("markup").with("value", markup));
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.SELECT, FORMATTER_ELEMENT)
                .label("Formatter")
                .markRequired()
                .value(formatters.formatter(formatterContext(settings)).id())
                .options(formatterIds().stream().map(id -> new SelectOption(id, id)).toList()));
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        return Map.of(FORMATTER, submitted.getOrDefault(FORMATTER_ELEMENT, ""));
    }

    /** The formatter has to be one the site has, whatever the browser sent. */
    @Override
    public Map<String, String> validateSettings(Map<String, String> submitted) {
        return formatterIds().contains(submitted.getOrDefault(FORMATTER_ELEMENT, ""))
                ? Map.of()
                : Map.of(FORMATTER_ELEMENT, UNKNOWN_FORMATTER_MESSAGE);
    }

    private boolean isOfThisBundle(EntityData entity) {
        return entity.entityType().equals(instance.entityTypeId()) && instance.bundle().equals(bundleOf(entity));
    }

    /** An unbundled type hangs its fields on its own name. */
    private static String bundleOf(EntityData entity) {
        return (entity.bundle() == null) ? entity.entityType() : entity.bundle();
    }

    /** The field under the formatter the settings name, or the field type's default when the site lacks it. */
    private FormatterContext formatterContext(Map<String, Object> settings) {
        String formatter = BlockSettings.string(settings, FORMATTER);
        Map<String, Object> formatterSettings =
                formatterIds().contains(formatter) ? Map.of(FORMATTER, formatter) : Map.of();
        return formatters.context(instance.entityTypeId(), instance.bundle(), instance.fieldName(), formatterSettings)
                .withoutLabel();
    }

    private List<String> formatterIds() {
        return plugins.managerFor(FieldFormatter.class).ids().stream().sorted().toList();
    }
}
