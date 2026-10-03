package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.util.HtmlUtils;

/** An Edit button for each result the reader may change and whose type has an edit form. */
@SpringDropPlugin(id = OperationsField.ID, type = FieldHandler.class)
public class OperationsField implements FieldHandler {

    public static final String ID = "entity_operations";

    private final EntityTypeManager entityTypes;
    private final EntityAccessManager access;

    public OperationsField(EntityTypeManager entityTypes, EntityAccessManager access) {
        this.entityTypes = entityTypes;
        this.access = access;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Operations";
    }

    @Override
    public boolean sortable() {
        return false;
    }

    @Override
    public String heading(HandlerConfig config) {
        String label = config.text(LABEL);
        return label.isEmpty() ? "Operations" : label;
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        return row.entity(config.relationship())
                .filter(entity -> access.may(entity.entityType(), entity, EntityAccessHandler.UPDATE))
                .flatMap(entity -> Optional.ofNullable(entityTypes.require(entity.entityType()).links()
                        .get("edit-form")).map(link -> link.replaceAll("\\{[^}]+}", String.valueOf(entity.id()))))
                .map(path -> "<a class=\"btn btn-secondary btn-sm\" href=\"" + HtmlUtils.htmlEscape(path)
                        + "\">Edit</a>")
                .orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(LABEL, Settings.submitted(prefix, LABEL, submitted));
    }
}
