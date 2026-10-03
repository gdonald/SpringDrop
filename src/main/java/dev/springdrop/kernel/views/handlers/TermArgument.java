package dev.springdrop.kernel.views.handlers;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.views.ArgumentHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Finds results tagged with a term in any of their reference fields to terms.
 * The handler's property names the entity type whose fields are searched. A
 * value that is not a term id, or a type with no such fields, finds nothing.
 */
@SpringDropPlugin(id = TermArgument.ID, type = ArgumentHandler.class)
public class TermArgument implements ArgumentHandler {

    public static final String ID = "taxonomy_term_any";

    private final FieldConfigManager fields;

    public TermArgument(FieldConfigManager fields) {
        this.fields = fields;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Has term";
    }

    @Override
    public Optional<Condition> condition(HandlerConfig config, String value) {
        if (!value.matches("\\d{1,18}")) {
            return Optional.empty();
        }
        long term = Long.parseLong(value);
        List<Condition> tagged = fields.storages(config.property()).stream()
                .filter(storage -> storage.type().equals(EntityReferenceFieldType.ID)
                        && EntityReferenceFieldType.targetType(storage).equals(TaxonomyEntityType.ID))
                .map(FieldStorageConfig::name)
                .map(field -> Condition.equal(field, term))
                .toList();
        return tagged.isEmpty() ? Optional.empty() : Optional.of(Condition.anyOf(tagged.toArray(Condition[]::new)));
    }

    @Override
    public List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return ValueArgument.defaultActionForm(prefix, settings);
    }

    @Override
    public Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        ValueArgument.putDefaultAction(prefix, submitted, values);
        return values;
    }
}
