package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * A box to tick on each result, choosing it for an action applied to every
 * ticked result at once. The boxes belong to the bulk form the page draws
 * above the listing, joined to it through their {@code form} attribute, so
 * the listing's own markup carries no form token and can be cached. The
 * {@code actions} setting names the actions offered, separated by commas;
 * left empty, every action for the listed type is offered.
 */
@SpringDropPlugin(id = BulkFormField.ID, type = FieldHandler.class)
public class BulkFormField implements FieldHandler {

    public static final String ID = "bulk_form";

    public static final String ACTIONS = "actions";

    /** The id of the form the boxes belong to. */
    public static final String FORM_ID = "views-bulk-form";

    /** The name each ticked box is sent under. */
    public static final String ITEMS = "items";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Bulk operations";
    }

    @Override
    public boolean sortable() {
        return false;
    }

    @Override
    public String heading(HandlerConfig config) {
        String label = config.text(LABEL);
        return label.isEmpty() ? "Select" : label;
    }

    @Override
    public String render(ResultRow row, HandlerConfig config) {
        return row.entity(config.relationship())
                .map(entity -> "<input class=\"form-check-input\" type=\"checkbox\" name=\"" + ITEMS + "\" value=\""
                        + HtmlUtils.htmlEscape(entity.entityType() + ":" + entity.id()) + "\" form=\"" + FORM_ID
                        + "\" data-bulk-row aria-label=\"" + HtmlUtils.htmlEscape("Select " + entity.label())
                        + "\">")
                .orElse("");
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, LABEL, "Label", settings),
                Settings.text(prefix, ACTIONS, "Actions offered, by id, separated by commas", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(LABEL, Settings.submitted(prefix, LABEL, submitted),
                ACTIONS, Settings.submitted(prefix, ACTIONS, submitted));
    }
}
