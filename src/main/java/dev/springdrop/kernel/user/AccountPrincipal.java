package dev.springdrop.kernel.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in account as Spring Security carries it: who they are, what they
 * may do, and whether the account is still open. The first account bypasses
 * permission checks, so a site can always be recovered by its owner.
 */
public class AccountPrincipal implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final long id;
    private final String username;
    private final String passwordHash;
    private final boolean active;
    // Declared as ArrayList rather than List because the principal is stored in
    // the session, and the field's declared type has to be serializable.
    private final ArrayList<GrantedAuthority> authorities;

    public AccountPrincipal(
            long id, String username, String passwordHash, boolean active, List<String> permissions) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.active = active;
        this.authorities = permissions.stream()
                .map(permission -> (GrantedAuthority) new SimpleGrantedAuthority(permission))
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
