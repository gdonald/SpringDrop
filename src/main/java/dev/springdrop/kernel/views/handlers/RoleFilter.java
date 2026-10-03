package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.views.FilterHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.Settings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds accounts holding a role, by the role's id in the list the property
 * holds, {@code roles} for accounts. Exposed, it offers the site's roles.
 */
@SpringDropPlugin(id = RoleFilter.ID, type = FilterHandler.class)
public class RoleFilter implements FilterHandler {

    public static final String ID = "user_role";

    private final RoleManager roles;

    public RoleFilter(RoleManager roles) {
        this.roles = roles;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Role";
    }

    @Override
    public Optional<Condition> condition(HandlerConfig config, String value) {
        String role = value.strip();
        if (role.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(role.matches("[a-z0-9_]{1,64}")
                ? Condition.contains(config.property(), "\"" + role + "\"")
                : Condition.in(config.property(), List.of()));
    }

    @Override
    public FormElement exposedElement(HandlerConfig config, String current) {
        List<SelectOption> options = new ArrayList<>(List.of(new SelectOption("", "Any")));
        roles.all().forEach(role -> options.add(new SelectOption(role.id(), role.label())));
        String label = config.text("label").isEmpty() ? "Role" : config.text("label");
        return FormElement.of(ElementType.SELECT, FilterHandler.identifier(config)).label(label).value(current)
                .options(options);
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, VALUE, "Role", settings),
                Settings.checkbox(prefix, EXPOSED, "Let the reader choose the role", settings, false),
                Settings.text(prefix, IDENTIFIER, "Name the role is given under", settings),
                Settings.text(prefix, "label", "Label", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(VALUE, Settings.submitted(prefix, VALUE, submitted));
        values.put(EXPOSED, Settings.ticked(prefix, EXPOSED, submitted));
        values.put(IDENTIFIER, Settings.submitted(prefix, IDENTIFIER, submitted));
        values.put("label", Settings.submitted(prefix, "label", submitted));
        return values;
    }
}
