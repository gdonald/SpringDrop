package dev.springdrop.kernel.user;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Closing an account, and deciding what happens to what it wrote. Content is
 * found by its owner base field, so any content type that records an owner is
 * covered without naming it here.
 */
@Component
public class AccountCancellationService {

    private final UserAccountService accounts;
    private final EntityCrudService entities;
    private final EntityTypeManager entityTypeManager;
    private final EntityQueryExecutor queries;

    public AccountCancellationService(
            UserAccountService accounts,
            EntityCrudService entities,
            EntityTypeManager entityTypeManager,
            EntityQueryExecutor queries) {
        this.accounts = accounts;
        this.entities = entities;
        this.entityTypeManager = entityTypeManager;
        this.queries = queries;
    }

    public void cancel(long accountId, CancellationMethod method) {
        if (method == CancellationMethod.BLOCK) {
            accounts.block(accountId);
        } else if (method == CancellationMethod.BLOCK_AND_UNPUBLISH) {
            accounts.block(accountId);
            overOwnedContent(accountId, (type, owned) -> unpublish(owned));
        } else if (method == CancellationMethod.REASSIGN_TO_ANONYMOUS) {
            overOwnedContent(accountId, (type, owned) -> reassign(owned));
            entities.delete(UserEntityType.ID, accountId);
        } else {
            overOwnedContent(accountId, (type, owned) -> entities.delete(type.id(), owned.id()));
            entities.delete(UserEntityType.ID, accountId);
        }
    }

    /** Every content entity of every type that records who owns it. */
    private void overOwnedContent(long accountId, OwnedContentAction action) {
        for (EntityType type : entityTypeManager.all()) {
            if (!ownsContent(type)) {
                continue;
            }
            for (Object id : queries.query(type.id())
                    .condition(Condition.equal(BaseFieldDefinition.OWNER, accountId))
                    .ids()) {
                entities.load(type.id(), id).ifPresent(owned -> action.apply(type, owned));
            }
        }
    }

    private void unpublish(EntityData owned) {
        Map<String, Object> values = new LinkedHashMap<>(owned.fields());
        values.put(BaseFieldDefinition.STATUS, false);
        entities.save(owned.withFields(values));
    }

    private void reassign(EntityData owned) {
        Map<String, Object> values = new LinkedHashMap<>(owned.fields());
        values.put(BaseFieldDefinition.OWNER, UserAccount.ANONYMOUS_ID);
        entities.save(owned.withFields(values));
    }

    private static boolean ownsContent(EntityType type) {
        return type.kind() == EntityKind.CONTENT
                && !UserEntityType.ID.equals(type.id())
                && type.baseFields().stream()
                        .anyMatch(field -> field.name().equals(BaseFieldDefinition.OWNER));
    }

    /** What to do with one piece of content the account owns. */
    @FunctionalInterface
    private interface OwnedContentAction {

        void apply(EntityType type, EntityData owned);
    }

    /** The methods a site offers when closing an account. */
    public List<CancellationMethod> methods() {
        return List.of(CancellationMethod.values());
    }
}
