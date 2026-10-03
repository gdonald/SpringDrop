package dev.springdrop.kernel.block.plugins;

import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.config.ConfigChangedEvent;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.formatter.FieldFormatterManager;
import dev.springdrop.kernel.plugin.DerivablePlugin;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.event.EventListener;

/**
 * Derives a field block for every field on every bundle, as
 * {@code field_block:<entity type>:<bundle>:<field>}. Adding or removing a
 * field drops the cached block plugins, so a new field has a block as soon as
 * it exists.
 */
@SpringDropPlugin(id = FieldBlockDeriver.ID, type = BlockPlugin.class)
public class FieldBlockDeriver implements DerivablePlugin<BlockPlugin> {

    public static final String ID = "field_block";

    private final FieldConfigManager fields;
    private final FieldFormatterManager formatters;
    private final PluginRegistry plugins;

    public FieldBlockDeriver(FieldConfigManager fields, FieldFormatterManager formatters, PluginRegistry plugins) {
        this.fields = fields;
        this.formatters = formatters;
        this.plugins = plugins;
    }

    /** The id of the block drawing one field of one bundle. */
    public static String blockId(String entityTypeId, String bundle, String fieldName) {
        return ID + ":" + entityTypeId + ":" + bundle + ":" + fieldName;
    }

    /** The start of the id of every field block of one bundle. */
    public static String bundlePrefix(String entityTypeId, String bundle) {
        return ID + ":" + entityTypeId + ":" + bundle + ":";
    }

    @Override
    public Map<String, BlockPlugin> derivatives() {
        Map<String, BlockPlugin> blocks = new LinkedHashMap<>();
        for (FieldInstanceConfig instance : fields.allInstances()) {
            blocks.put(instance.entityTypeId() + ":" + instance.bundle() + ":" + instance.fieldName(),
                    new FieldBlock(instance, formatters, plugins));
        }
        return blocks;
    }

    @EventListener
    void fieldsChanged(ConfigChangedEvent event) {
        if (event.name().startsWith(FieldInstanceConfig.CONFIG_PREFIX + ".")) {
            plugins.invalidate(BlockPlugin.class);
        }
    }
}
