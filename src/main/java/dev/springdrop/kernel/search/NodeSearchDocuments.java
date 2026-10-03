package dev.springdrop.kernel.search;

import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.node.NodeEntityType;
import org.springframework.stereotype.Component;

/** Makes content searchable by its title and its text fields. */
@Component
public class NodeSearchDocuments extends TextFieldsDocumentBuilder {

    public NodeSearchDocuments(FieldConfigManager fields) {
        super(NodeEntityType.ID, fields);
    }
}
