package dev.springdrop.kernel.entity.query;

import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Applies the access rules of an entity type to any query tagged for them, so
 * what a listing shows and what a page shows agree. The first account sees
 * everything, so its queries are left alone.
 */
@Component
public class EntityQueryAccessFilter {

    /** The tag a query carries to ask for access rules to be applied. */
    public static final String ACCESS_TAG_SUFFIX = "_access";

    private final List<EntityQueryAccessRule> rules;

    public EntityQueryAccessFilter(List<EntityQueryAccessRule> rules) {
        this.rules = rules;
    }

    public static String accessTagFor(String entityTypeId) {
        return entityTypeId + ACCESS_TAG_SUFFIX;
    }

    @EventListener
    public void narrow(EntityQueryAlterEvent event) {
        EntityQuery query = event.subject();
        if (!query.accessTags().contains(accessTagFor(query.entityTypeId()))) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (AccountPrincipals.bypassesChecks(authentication)) {
            return;
        }
        rules.stream()
                .filter(rule -> rule.entityTypeId().equals(query.entityTypeId()))
                .forEach(rule -> rule.narrow(query, authentication));
    }
}
