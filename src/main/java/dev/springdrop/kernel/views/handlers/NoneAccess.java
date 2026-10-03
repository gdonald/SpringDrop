package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.views.AccessPlugin;
import dev.springdrop.kernel.views.PluginConfig;
import org.springframework.security.core.Authentication;

/** Anyone may see the view. Each result still goes by its own access. */
@SpringDropPlugin(id = NoneAccess.ID, type = AccessPlugin.class)
public class NoneAccess implements AccessPlugin {

    public static final String ID = "none";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Unrestricted";
    }

    @Override
    public boolean allows(PluginConfig config, Authentication authentication) {
        return true;
    }
}
