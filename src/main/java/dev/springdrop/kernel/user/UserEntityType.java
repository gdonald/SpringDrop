package dev.springdrop.kernel.user;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The user entity type: fieldable like any other content entity, so a site can
 * add fields to accounts, with the account's own columns defined in code. It has
 * no bundles, so its fields hang on the type's own name and every account
 * carries the same set.
 */
@Component
public class UserEntityType implements EntityTypeProvider {

    public static final String ID = "user";

    public static final String MAIL = "mail";

    public static final String PASSWORD_HASH = "password_hash";

    public static final String LAST_ACCESS = "last_access";

    public static final String LAST_LOGIN = "last_login";

    public static final String TIMEZONE = "timezone";

    public static final String PREFERRED_LANGUAGE = "preferred_language";

    /** The roles an account holds, on top of the one everyone signed in has. */
    public static final String ROLES = "roles";

    public static EntityType definition() {
        return EntityType.content(ID, UserAccount.class)
                .withBaseFields(List.of(
                        BaseFieldDefinition.required(MAIL, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(PASSWORD_HASH, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(BaseFieldDefinition.STATUS, ColumnType.BOOLEAN),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CREATED, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(LAST_ACCESS, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(LAST_LOGIN, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(TIMEZONE, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(PREFERRED_LANGUAGE, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(ROLES, ColumnType.JSONB)));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition());
    }
}
