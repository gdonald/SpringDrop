package dev.springdrop.kernel.block.conditions;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockSettings;
import dev.springdrop.kernel.block.VisibilityCondition;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shows a block to people holding any of the roles chosen. */
@SpringDropPlugin(id = UserRoleCondition.ID, type = VisibilityCondition.class)
public class UserRoleCondition implements VisibilityCondition {

    public static final String ID = "user_role";

    public static final String ROLES = "roles";

    public static final String USER_ROLES_CONTEXT = "user.roles";

    private final RoleManager roles;

    public UserRoleCondition(RoleManager roles) {
        this.roles = roles;
    }

    @Override
    public String label() {
        return "Roles";
    }

    @Override
    public boolean evaluate(Map<String, Object> settings, BlockContext context) {
        Set<String> held = AccountPrincipals.rolesOf(SecurityContextHolder.getContext().getAuthentication());
        return BlockSettings.strings(settings, ROLES).stream().anyMatch(held::contains);
    }

    @Override
    public CacheMetadata cacheability() {
        return CacheMetadata.EMPTY.withContext(USER_ROLES_CONTEXT);
    }

    /** One box per role, ticked for the roles the placement already names. */
    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        List<String> chosen = BlockSettings.strings(settings, ROLES);
        return roles.all().stream()
                .map(role -> FormElement.of(ElementType.CHECKBOX, prefix + role.id())
                        .label(role.label())
                        .value(chosen.contains(role.id())))
                .toList();
    }

    @Override
    public Optional<Map<String, Object>> settingsValues(String prefix, Map<String, String> submitted) {
        List<String> chosen = roles.all().stream()
                .map(RoleConfig::id)
                .filter(roleId -> submitted.containsKey(prefix + roleId))
                .toList();
        return chosen.isEmpty() ? Optional.empty() : Optional.of(Map.of(ROLES, chosen));
    }
}
