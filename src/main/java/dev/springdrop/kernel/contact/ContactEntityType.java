package dev.springdrop.kernel.contact;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Contact messages: what someone sent through a contact form, kept beside the
 * mail it became. They come in contact forms, which carry the fields their
 * messages hold, and a message's label is its subject.
 */
@Component
public class ContactEntityType implements EntityTypeProvider {

    public static final String ID = "contact_message";

    public static final String FORM_ID = "contact_form";

    public static final String BUNDLE_KEY = "contact_form";

    /** The name the sender gave. */
    public static final String NAME = "name";

    /** The address the sender gave. */
    public static final String MAIL = "mail";

    public static final String MESSAGE = "message";

    /** Whether the sender asked for a copy. */
    public static final String COPY = "copy";

    /** The account a personal message was sent to. */
    public static final String RECIPIENT = "recipient";

    /** The address the message was sent from. */
    public static final String IP = "ip";

    public static EntityType definition() {
        return EntityType.content(ID, EntityData.class)
                .withBundles(BUNDLE_KEY, FORM_ID)
                .withBaseFields(List.of(
                        BaseFieldDefinition.optional(NAME, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(MAIL, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(MESSAGE, ColumnType.TEXT),
                        BaseFieldDefinition.optional(COPY, ColumnType.BOOLEAN),
                        BaseFieldDefinition.optional(RECIPIENT, ColumnType.BIGINT),
                        BaseFieldDefinition.optional(IP, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(BaseFieldDefinition.OWNER, ColumnType.BIGINT),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CREATED, ColumnType.TIMESTAMP)));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition(), EntityType.config(FORM_ID, ContactForm.class));
    }
}
