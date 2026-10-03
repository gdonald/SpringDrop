package dev.springdrop.kernel.field.widget.types;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.widget.FieldWidget;
import dev.springdrop.kernel.field.widget.FieldWidgetPaths;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A text input that suggests entities to refer to as the person types, and holds
 * the one they pick as {@code Label (id)}. A field whose instance turns on
 * {@code auto_create} accepts a name that matches nothing and creates the target
 * as the reference is read, which is how tagging works, provided the person may
 * create it.
 */
@SpringDropPlugin(id = EntityReferenceAutocompleteWidget.ID, type = FieldWidget.class)
public class EntityReferenceAutocompleteWidget implements FieldWidget {

    public static final String ID = "entity_reference_autocomplete";

    /** Lets a name that matches nothing create the entity it names. */
    public static final String AUTO_CREATE = "auto_create";

    /** The bundle a created target is given, when the field allows more than one. */
    public static final String AUTO_CREATE_BUNDLE = "auto_create_bundle";

    private static final Pattern LABELLED_ID = Pattern.compile(".*\\((\\d+)\\)\\s*$");

    private final EntityCrudService entities;
    private final EntityAccessManager entityAccess;

    public EntityReferenceAutocompleteWidget(EntityCrudService entities, EntityAccessManager entityAccess) {
        this.entities = entities;
        this.entityAccess = entityAccess;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public FormElement element(WidgetContext context, int delta, Object value) {
        return withSuggestions(context, Widgets.control(ElementType.TEXTFIELD, context, delta, label(context, value)));
    }

    /** The input with the suggestion list the server fills as the person types. */
    public static FormElement withSuggestions(WidgetContext context, FormElement input) {
        return input
                .attribute("autocomplete", "off")
                .attribute("list", context.fieldName() + "-suggestions")
                .attribute("hx-get", FieldWidgetPaths.AUTOCOMPLETE)
                .attribute("hx-trigger", "keyup changed delay:200ms")
                .attribute("hx-target", "#" + context.fieldName() + "-suggestions")
                .attribute("hx-vals", hxVals(context));
    }

    @Override
    public Object extract(WidgetContext context, int delta, Map<String, Object> submitted) {
        Object entered = submitted.get(context.elementName(delta));
        if (entered == null || entered.toString().isBlank()) {
            return null;
        }

        return resolve(context, entered.toString().trim());
    }

    /**
     * The target a piece of entered text refers to: the id it names, or for a
     * free-tagging field a target created with it as its label. Text that names
     * no id and creates nothing is kept as entered, which the field's reference
     * check then refuses.
     */
    public Object resolve(WidgetContext context, String text) {
        return referencedId(text).orElseGet(() -> created(context, text));
    }

    /**
     * The reference as the person sees it: the target's label with its id. Text
     * that names no id, kept from a refused submission, is shown as entered.
     */
    public String label(WidgetContext context, Object value) {
        if (value == null) {
            return "";
        }
        if (!String.valueOf(value).matches("\\d{1,18}")) {
            return value.toString();
        }
        return entities.load(targetType(context), Long.valueOf(String.valueOf(value)))
                .map(target -> target.label() + " (" + value + ")")
                .orElse(value.toString());
    }

    private static Optional<Object> referencedId(String text) {
        Matcher labelled = LABELLED_ID.matcher(text);
        if (labelled.matches()) {
            return Optional.of(Long.valueOf(labelled.group(1)));
        }
        return text.chars().allMatch(Character::isDigit)
                ? Optional.of(Long.valueOf(text))
                : Optional.empty();
    }

    /**
     * Creates the target a free-tagging field was given the name of, when the
     * person may create one of that bundle, or of the type for an unbundled one.
     */
    private Object created(WidgetContext context, String label) {
        if (!Boolean.TRUE.equals(context.instance().settings().get(AUTO_CREATE))) {
            return label;
        }
        Object bundle = context.instance().settings().get(AUTO_CREATE_BUNDLE);
        String createdBundle = (bundle == null) ? null : bundle.toString();
        String targetType = targetType(context);
        if (!entityAccess.may(targetType, (createdBundle == null) ? targetType : createdBundle,
                EntityAccessHandler.CREATE)) {
            return label;
        }
        EntityData target = new EntityData(targetType, null, null, createdBundle, label,
                EntityData.DEFAULT_LANGCODE, null, Map.of());
        return entities.save(target).id();
    }

    /** The entity type this field points at. */
    public static String targetType(WidgetContext context) {
        return EntityReferenceFieldType.targetType(context.storage());
    }

    private static String hxVals(WidgetContext context) {
        return "{\"entity_type\":\"" + context.instance().entityTypeId()
                + "\",\"bundle\":\"" + context.instance().bundle()
                + "\",\"field\":\"" + context.fieldName() + "\"}";
    }
}
