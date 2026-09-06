package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.LinkConstraint;
import java.util.List;
import java.util.Map;

/**
 * A link: a uri and an optional title. An internal link is written
 * {@code internal:/path} and resolves to the route serving that path; anything
 * else is an absolute uri to somewhere off the site.
 */
@SpringDropPlugin(id = LinkFieldType.ID, type = FieldType.class)
public class LinkFieldType implements FieldType {

    public static final String ID = "link";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(
                FieldProperty.required(LinkConstraint.URI_KEY, ColumnType.VARCHAR),
                new FieldProperty(LinkConstraint.TITLE_KEY, ColumnType.VARCHAR, false));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", LinkConstraint.ID));
    }

    @Override
    public String defaultWidget() {
        return "link_default";
    }

    @Override
    public String defaultFormatter() {
        return "link";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
