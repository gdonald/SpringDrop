package dev.springdrop.kernel.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in account as Spring Security carries it: who they are, what they
 * may do, and whether the account is still open. The first account bypasses
 * permission checks, so a site can always be recovered by its owner.
 *
 * <p>The roles the account holds are carried as authorities too, named with
 * Spring Security's {@code ROLE_} prefix so they never read as a permission.
 */
public class AccountPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    public static final String ROLE_PREFIX = "ROLE_";

    private final long id;
    private final String username;
    private final String passwordHash;
    private final boolean active;
    // Declared as ArrayList rather than List because the principal is stored in
    // the session, and the field's declared type has to be serializable.
    private final ArrayList<GrantedAuthority> authorities;

    public AccountPrincipal(
            long id, String username, String passwordHash, boolean active, List<String> permissions) {
        this(id, username, passwordHash, active, permissions, List.of());
    }

    public AccountPrincipal(
            long id,
            String username,
            String passwordHash,
            boolean active,
            List<String> permissions,
            List<String> roles) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.active = active;
        this.authorities = Stream.concat(permissions.stream(), roles.stream().map(role -> ROLE_PREFIX + role))
                .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public long id() {
        return id;
    }

    public boolean bypassesAccessChecks() {
        return id == UserAccount.ADMINISTRATOR_ID;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.unmodifiableList(authorities);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
