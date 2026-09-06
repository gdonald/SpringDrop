package dev.springdrop.kernel.user;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.schema.SchemaManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Accounts: creating them, finding them, and the two the site always has. A name
 * or an email address belongs to one account whatever case it was typed in,
 * which the database enforces and this service reports in words.
 */
@Component
public class UserAccountService {

    private final EntityCrudService entities;
    private final EntityTypeManager entityTypeManager;
    private final EntityQueryExecutor queries;
    private final SchemaManager schemaManager;

    public UserAccountService(
            EntityCrudService entities,
            EntityTypeManager entityTypeManager,
            EntityQueryExecutor queries,
            SchemaManager schemaManager) {
        this.entities = entities;
        this.entityTypeManager = entityTypeManager;
        this.queries = queries;
        this.schemaManager = schemaManager;
    }

    /** Creates the account storage, its unique indexes, and the two reserved accounts. */
    public void install() {
        entityTypeManager.installStorage(UserEntityType.ID);
        schemaManager.createUniqueIndex("user_name_unique", UserEntityType.ID, List.of("lower(label)"));
        schemaManager.createUniqueIndex("user_mail_unique", UserEntityType.ID, List.of("lower(mail)"));

        reserve(UserAccount.ANONYMOUS_ID, UserAccount.ANONYMOUS_NAME, "");
        reserve(UserAccount.ADMINISTRATOR_ID, "admin", "admin@example.com");
    }

    public UserAccount create(String name, String mail, String passwordHash) {
        return create(name, mail, passwordHash, Map.of());
    }

    /** Creates an account, along with whatever fields the site has added to accounts. */
    public UserAccount create(String name, String mail, String passwordHash, Map<String, Object> fields) {
        Map<String, Object> values = new LinkedHashMap<>(fields);
        values.put(UserEntityType.MAIL, mail);
        values.put(UserEntityType.PASSWORD_HASH, passwordHash);
        values.put("status", true);

        try {
            EntityData saved = entities.save(new EntityData(
                    UserEntityType.ID, null, null, null, name, EntityData.DEFAULT_LANGCODE, null, values));
            return accountOf(saved);
        } catch (DataIntegrityViolationException clash) {
            throw new DuplicateAccountException(
                    "An account already holds the name '" + name + "' or the address '" + mail + "'");
        }
    }

    public Optional<UserAccount> find(long id) {
        return entities.load(UserEntityType.ID, id).map(UserAccountService::accountOf);
    }

    public Optional<EntityData> load(long id) {
        return entities.load(UserEntityType.ID, id);
    }

    public Optional<UserAccount> findByName(String name) {
        return loadByName(name).map(UserAccountService::accountOf);
    }

    /** The stored account behind a name, with whatever fields it carries. */
    public Optional<EntityData> loadByName(String name) {
        return queries.query(UserEntityType.ID)
                .condition(Condition.equal("label", name))
                .ids().stream()
                .findFirst()
                .flatMap(id -> entities.load(UserEntityType.ID, id));
    }

    public void block(long id) {
        entities.load(UserEntityType.ID, id).ifPresent(account -> {
            Map<String, Object> values = new LinkedHashMap<>(account.fields());
            values.put("status", false);
            entities.save(account.withFields(values));
        });
    }

    private void reserve(long id, String name, String mail) {
        if (entities.load(UserEntityType.ID, id).isEmpty()) {
            entities.save(new EntityData(UserEntityType.ID, id, null, null, name,
                    EntityData.DEFAULT_LANGCODE, null,
                    Map.of(UserEntityType.MAIL, mail, "status", id != UserAccount.ANONYMOUS_ID)));
        }
    }

    private static UserAccount accountOf(EntityData entity) {
        Map<String, Object> fields = entity.fields();
        return new UserAccount(
                ((Number) entity.id()).longValue(),
                entity.label(),
                String.valueOf(fields.getOrDefault(UserEntityType.MAIL, "")),
                Boolean.TRUE.equals(fields.get("status")),
                String.valueOf(fields.getOrDefault(UserEntityType.TIMEZONE, "UTC")),
                String.valueOf(fields.getOrDefault(UserEntityType.PREFERRED_LANGUAGE,
                        EntityData.DEFAULT_LANGCODE)));
    }
}
