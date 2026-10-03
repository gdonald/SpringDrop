package dev.springdrop.kernel.filter;

import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import org.springframework.stereotype.Component;

/**
 * Gives the roles a site starts with the formats they write in: Restricted
 * HTML to someone not signed in, Basic HTML to everyone signed in. An install
 * profile asks for it, so a site that set its own access keeps it.
 */
@Component
public class DefaultTextFormatAccess {

    private final RoleManager roles;

    public DefaultTextFormatAccess(RoleManager roles) {
        this.roles = roles;
    }

    public void grant() {
        roles.install();
        roles.grant(RoleConfig.ANONYMOUS, TextFormatManager.permission(TextFormatManager.RESTRICTED_HTML));
        roles.grant(RoleConfig.AUTHENTICATED, TextFormatManager.permission(TextFormatManager.BASIC_HTML));
    }
}
