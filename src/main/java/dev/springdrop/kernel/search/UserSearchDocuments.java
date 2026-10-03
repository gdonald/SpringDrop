package dev.springdrop.kernel.search;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.user.UserEntityType;
import org.springframework.stereotype.Component;

/** Makes accounts searchable by their names and text fields. */
@Component
public class UserSearchDocuments extends TextFieldsDocumentBuilder {

    public UserSearchDocuments(FieldConfigManager fields) {
        super(UserEntityType.ID, fields);
    }
}
