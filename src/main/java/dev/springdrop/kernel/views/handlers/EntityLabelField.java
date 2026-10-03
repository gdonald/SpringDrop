package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/** The entity's label, linked to its page unless the {@code link} setting is off. */
@SpringDropPlugin(id = EntityLabelField.ID, type = FieldHandler.class)
public class EntityLabelField implements FieldHandler {

    public static final String ID = "entity_label";

    public static final String LINK = "link";

    private final EntityTypeManager entityTypes;
    private final PathAliasManager aliases;

    public EntityLabelField(EntityTypeManager entityTypes, PathAliasManager aliases) {
        this.entityTypes = entityTypes;
        this.aliases = aliases;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Label";
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        return row.entity(config.relationship()).map(entity -> {
            String label = HtmlUtils.htmlEscape(entity.label());
            if ("false".equals(String.valueOf(config.setting(LINK, true)))) {
                return label;
            }
            String path = aliases.outbound(entityTypes.require(entity.entityType()).canonicalPath(entity.id()));
            return "<a href=\"" + HtmlUtils.htmlEscape(path) + "\">" + label + "</a>";
        }).orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings),
                Settings.checkbox(prefix, LINK, "Link to the entity", settings, true));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(LABEL, Settings.submitted(prefix, LABEL, submitted));
        values.put(LINK, Settings.ticked(prefix, LINK, submitted));
        return values;
    }
}
