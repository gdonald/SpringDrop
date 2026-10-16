package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The administration toolbar: each section of the Administration menu the
 * person may reach, with the pages under it, and shortcuts to their account
 * and to signing out. It is shown to the first account and anyone holding
 * {@code access toolbar}. A section whose link is expanded, since its page
 * only lists the pages under it, is left out when none of those may be reached.
 */
@Component
public class AdminToolbar {

    private final MenuTreeBuilder trees;

    public AdminToolbar(MenuTreeBuilder trees) {
        this.trees = trees;
    }

    /** The sections of a built Administration menu, without those listing pages of which none may be reached. */
    public static List<MenuTreeItem> reachableSections(List<MenuTreeItem> sections) {
        return sections.stream().filter(section -> !section.link().expanded() || !section.children().isEmpty())
                .toList();
    }

    /** The toolbar for the person making the current request, built when a template first asks for it. */
    public View view(String activePath) {
        return new View(this, SecurityContextHolder.getContext().getAuthentication(), activePath);
    }

    /** What the toolbar draws for one request. */
    public static final class View {

        private final AdminToolbar toolbar;
        private final Authentication who;
        private final String activePath;

        View(AdminToolbar toolbar, Authentication who, String activePath) {
            this.toolbar = toolbar;
            this.who = who;
            this.activePath = activePath;
        }

        public boolean shown() {
            return AccountPrincipals.bypassesChecks(who) || (who != null && who.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals(Permissions.ACCESS_TOOLBAR)));
        }

        public List<MenuTreeItem> sections() {
            return reachableSections(toolbar.trees.build(MenuConfig.ADMIN, activePath).items());
        }

        public String accountName() {
            return who != null && who.getPrincipal() instanceof AccountPrincipal account ? account.getUsername() : "";
        }

        public String logoutPath() {
            return SecurityConfig.LOGOUT_PATH;
        }
    }
}
