package dev.springdrop.web;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.FieldWidgetPaths;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.field.widget.types.EntityReferenceAutocompleteWidget;
import dev.springdrop.kernel.form.FormRenderer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * Serves the actions a field's widget needs while a form is being filled in:
 * suggesting the entities a reference field could point at, and adding one more
 * item to a multi-value field. Adding an item posts the form as it stands and
 * gets back the field one delta longer, which htmx swaps in place, so the rest
 * of the form is left alone and nothing is lost.
 */
@Controller
public class FieldWidgetController {

    public static final String ENTITY_TYPE = "entity_type";

    public static final String BUNDLE = "bundle";

    public static final String FIELD = "field";

    /** How many suggestions the list offers at once. */
    private static final int SUGGESTION_LIMIT = 10;

    private final FieldWidgetManager widgets;
    private final FormRenderer renderer;
    private final EntityQueryExecutor queries;
    private final EntityCrudService entities;
    private final EntityTypeManager entityTypeManager;

    public FieldWidgetController(
            FieldWidgetManager widgets,
            FormRenderer renderer,
            EntityQueryExecutor queries,
            EntityCrudService entities,
            EntityTypeManager entityTypeManager) {
        this.widgets = widgets;
        this.renderer = renderer;
        this.queries = queries;
        this.entities = entities;
        this.entityTypeManager = entityTypeManager;
    }

    /**
     * The entities a reference field could point at, narrowed by what has been
     * typed so far and by what the person is allowed to see. The list is a
     * datalist, so it works as a plain suggestion list without scripting.
     */
    @GetMapping(FieldWidgetPaths.AUTOCOMPLETE)
    public ResponseEntity<String> autocomplete(
            @RequestParam(ENTITY_TYPE) String entityTypeId,
            @RequestParam(BUNDLE) String bundle,
            @RequestParam(FIELD) String fieldName,
            @RequestParam(name = "q", defaultValue = "") String typed,
            Authentication authentication) {

        WidgetContext context = widgets.context(entityTypeId, bundle, fieldName);
        String targetType = EntityReferenceAutocompleteWidget.targetType(context);

        StringBuilder options = new StringBuilder();
        for (Object id : matchingIds(targetType, typed)) {
            entities.load(targetType, id)
                    .filter(target -> mayView(targetType, target, authentication))
                    .ifPresent(target -> options.append(option(target)));
        }

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(
                "<datalist id=\"" + HtmlUtils.htmlEscape(fieldName) + "-suggestions\">"
                        + options + "</datalist>");
    }

    private List<Object> matchingIds(String targetType, String typed) {
        var query = queries.query(targetType).sort(Sort.ascending("label")).range(0, SUGGESTION_LIMIT);
        if (!typed.isBlank()) {
            query.condition(Condition.contains("label", typed));
        }
        return query.ids();
    }

    private boolean mayView(String targetType, EntityData target, Authentication authentication) {
        return entityTypeManager.accessHandlerFor(targetType)
                .check(entityTypeManager.require(targetType), target,
                        EntityAccessHandler.VIEW, authentication)
                .allowed();
    }

    private static String option(EntityData target) {
        String value = target.label() + " (" + target.id() + ")";
        return "<option value=\"" + HtmlUtils.htmlEscape(value) + "\"></option>";
    }

    @PostMapping(FieldWidgetPaths.ADD_MORE)
    public ResponseEntity<String> addMore(
            @RequestParam(ENTITY_TYPE) String entityTypeId,
            @RequestParam(BUNDLE) String bundle,
            @RequestParam(FIELD) String fieldName,
            @RequestParam Map<String, String> submitted) {

        WidgetContext context = widgets.context(entityTypeId, bundle, fieldName);
        List<Object> values = widgets.extract(context, submittedValues(submitted));

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(renderer.render(widgets.build(context, values, 1)));
    }

    private static Map<String, Object> submittedValues(Map<String, String> submitted) {
        return new LinkedHashMap<>(submitted);
    }
}
