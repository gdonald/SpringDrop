package dev.springdrop.kernel.user;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Finds the account behind a name at sign-in, and works out what it may do from
 * the roles it holds. A blocked account is loaded as disabled rather than
 * hidden, so the login is refused for what it is rather than looking like a name
 * that does not exist. An account that has been guessed at too often lately is
 * refused before its password is even looked at.
 */
@Component
public class AccountDetailsService implements UserDetailsService {

    private final UserAccountService accounts;
    private final RoleManager roles;
    private final FloodService flood;

    public AccountDetailsService(UserAccountService accounts, RoleManager roles, FloodService flood) {
        this.accounts = accounts;
        this.roles = roles;
        this.flood = flood;
    }

    /** Everyone signed in holds the authenticated role, on top of their own. */
    private static List<String> rolesOf(Map<String, Object> fields) {
        List<String> held = new ArrayList<>();
        held.add(RoleConfig.AUTHENTICATED);
        Object stored = fields.get(UserEntityType.ROLES);
        if (stored instanceof List<?> names) {
            names.forEach(name -> held.add(String.valueOf(name)));
        }
        return held;
    }

    @Override
    public AccountPrincipal loadUserByUsername(String username) {
        if (!flood.isAllowed(FloodSettings.LOGIN_USER, username,
                FloodSettings.USER_THRESHOLD, FloodSettings.USER_WINDOW)) {
            throw new AccountFloodedException(
                    "Too many failed sign-ins for '" + username + "'. Try again later.");
        }

        EntityData stored = accounts.loadByName(username).orElseThrow(
                () -> new UsernameNotFoundException("No account named '" + username + "'"));

        Map<String, Object> fields = stored.fields();
        return new AccountPrincipal(
                ((Number) stored.id()).longValue(),
                stored.label(),
                String.valueOf(fields.getOrDefault(UserEntityType.PASSWORD_HASH, "")),
                Boolean.TRUE.equals(fields.get("status")),
                roles.permissionsOf(rolesOf(fields)));
    }
}
