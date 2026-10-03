package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.user.AccountPrincipals;
import dev.springdrop.kernel.views.AccessPlugin;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.Settings;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;

/** Someone holding the permission the {@code permission} setting names may see the view. */
@SpringDropPlugin(id = PermissionAccess.ID, type = AccessPlugin.class)
public class PermissionAccess implements AccessPlugin {

    public static final String ID = "permission";

    public static final String PERMISSION = "permission";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Permission";
    }

    @Override
    public boolean allows(PluginConfig config, Authentication authentication) {
        String permission = String.valueOf(config.setting(PERMISSION, ""));
        return AccountPrincipals.bypassesChecks(authentication) || authentication != null
                && authentication.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of(Settings.text(prefix, PERMISSION, "Permission", settings));
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of(PERMISSION, Settings.submitted(prefix, PERMISSION, submitted));
    }
}
