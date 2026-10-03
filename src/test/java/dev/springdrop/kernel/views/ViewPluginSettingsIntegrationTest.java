package dev.springdrop.kernel.views;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.field.formatter.FieldFormatterManager;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.views.handlers.BaseValueField;
import dev.springdrop.kernel.views.handlers.EntityFieldField;
import dev.springdrop.kernel.views.handlers.EntityLabelField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.LinksField;
import dev.springdrop.kernel.views.handlers.MiniPager;
import dev.springdrop.kernel.views.handlers.NoneAccess;
import dev.springdrop.kernel.views.handlers.NonePager;
import dev.springdrop.kernel.views.handlers.OperationsField;
import dev.springdrop.kernel.views.handlers.PermissionAccess;
import dev.springdrop.kernel.views.handlers.ReferenceRelationship;
import dev.springdrop.kernel.views.handlers.RoleFilter;
import dev.springdrop.kernel.views.handlers.SomePager;
import dev.springdrop.kernel.views.handlers.StandardSort;
import dev.springdrop.kernel.views.handlers.TermArgument;
import dev.springdrop.kernel.views.handlers.ValueArgument;
import dev.springdrop.kernel.views.handlers.ValueFilter;
import dev.springdrop.kernel.views.rows.EntityRow;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.GridStyle;
import dev.springdrop.kernel.views.styles.HtmlListStyle;
import dev.springdrop.kernel.views.styles.TableStyle;
import dev.springdrop.kernel.views.styles.UnformattedStyle;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ViewPluginSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String P = "s_";

    private static final String TICK = FormRenderer.CHECKED_VALUE;

    @Autowired
    private PluginRegistry registry;

    private <T extends ViewPlugin> T plugin(Class<T> type, String id) {
        return registry.managerFor(type).get(id);
    }

    private static List<String> names(List<FormElement> form) {
        return form.stream().map(FormElement::name).toList();
    }

    @Test
    void everyPluginIsFoundByItsOwnId() {
        for (Class<? extends ViewPlugin> type : ViewEditing.PLUGIN_TYPES.values()) {
            var plugins = registry.managerFor(type);
            plugins.ids().forEach(id -> assertThat(plugins.get(id).id()).isEqualTo(id));
        }
    }

    @Test
    void fieldHandlersTakeALabelAndTheirOwnSettings() {
        assertThat(plugin(FieldHandler.class, EntityLabelField.ID).settingsValues(P, Map.of(P + "label", " Title ",
                P + EntityLabelField.LINK, TICK))).isEqualTo(Map.of("label", "Title", EntityLabelField.LINK, true));
        assertThat(names(plugin(FieldHandler.class, EntityLabelField.ID).settingsForm(P, Map.of())))
                .containsExactly(P + "label", P + EntityLabelField.LINK);
        assertThat(plugin(FieldHandler.class, BaseValueField.ID).settingsValues(P, Map.of(P + "label", "Status")))
                .isEqualTo(Map.of("label", "Status", BaseValueField.TRUE_LABEL, "Yes", BaseValueField.FALSE_LABEL,
                        "No"));
        assertThat(plugin(FieldHandler.class, BaseValueField.ID).settingsValues(P, Map.of(P + BaseValueField.TRUE_LABEL,
                "On", P + BaseValueField.FALSE_LABEL, "Off"))).containsEntry(BaseValueField.TRUE_LABEL, "On")
                .containsEntry(BaseValueField.FALSE_LABEL, "Off");
        assertThat(names(plugin(FieldHandler.class, BaseValueField.ID).settingsForm(P, Map.of()))).hasSize(3);
        assertThat(plugin(FieldHandler.class, EntityFieldField.ID).settingsValues(P, Map.of(P
                + FieldFormatterManager.FORMATTER_SETTING, "string"))).containsEntry(
                FieldFormatterManager.FORMATTER_SETTING, "string");
        assertThat(plugin(FieldHandler.class, EntityFieldField.ID).settingsValues(P, Map.of()))
                .isEqualTo(Map.of("label", ""));
        assertThat(names(plugin(FieldHandler.class, EntityFieldField.ID).settingsForm(P, Map.of()))).hasSize(2);
        for (String id : List.of(OperationsField.ID, LinksField.ID)) {
            assertThat(plugin(FieldHandler.class, id).settingsValues(P, Map.of(P + "label", "Actions")))
                    .isEqualTo(Map.of("label", "Actions"));
            assertThat(names(plugin(FieldHandler.class, id).settingsForm(P, Map.of()))).containsExactly(P + "label");
        }
    }

    @Test
    void filtersTakeTheirComparisonAndWhetherTheReaderGivesTheValue() {
        Map<String, Object> value = plugin(FilterHandler.class, ValueFilter.ID).settingsValues(P, Map.of(
                P + ValueFilter.OPERATOR, "contains", P + FilterHandler.VALUE, " x ", P + "type", "number",
                P + FilterHandler.EXPOSED, TICK, P + FilterHandler.IDENTIFIER, "q"));
        Map<String, Object> fallback = plugin(FilterHandler.class, ValueFilter.ID).settingsValues(P, Map.of(
                P + ValueFilter.OPERATOR, "like", P + "type", "date"));

        assertThat(value).containsEntry(ValueFilter.OPERATOR, "contains").containsEntry(FilterHandler.VALUE, "x")
                .containsEntry("type", "number").containsEntry(FilterHandler.EXPOSED, true)
                .containsEntry(FilterHandler.IDENTIFIER, "q");
        assertThat(fallback).containsEntry(ValueFilter.OPERATOR, "=").containsEntry("type", "text")
                .containsEntry(FilterHandler.EXPOSED, false);
        assertThat(names(plugin(FilterHandler.class, ValueFilter.ID).settingsForm(P, Map.of()))).hasSize(8);
        assertThat(plugin(FilterHandler.class, RoleFilter.ID).settingsValues(P, Map.of(P + FilterHandler.VALUE,
                "editor", P + FilterHandler.EXPOSED, TICK))).containsEntry(FilterHandler.VALUE, "editor")
                .containsEntry(FilterHandler.EXPOSED, true);
        assertThat(names(plugin(FilterHandler.class, RoleFilter.ID).settingsForm(P, Map.of()))).hasSize(4);
    }

    @Test
    void sortsArgumentsAndRelationshipsTakeTheirSettings() {
        assertThat(plugin(SortHandler.class, StandardSort.ID).settingsValues(P, Map.of(P + SortHandler.ORDER,
                "desc", P + ViewExecutor.EXPOSED, TICK, P + "label", "Newest"))).isEqualTo(Map.of(
                SortHandler.ORDER, "desc", ViewExecutor.EXPOSED, true, "label", "Newest"));
        assertThat(names(plugin(SortHandler.class, StandardSort.ID).settingsForm(P, Map.of()))).hasSize(3);
        assertThat(plugin(ArgumentHandler.class, ValueArgument.ID).settingsValues(P, Map.of(P + "type", "number",
                P + ArgumentHandler.DEFAULT_ACTION, ArgumentHandler.FIXED, P + ArgumentHandler.DEFAULT_VALUE, "3")))
                .isEqualTo(Map.of("type", "number", ArgumentHandler.DEFAULT_ACTION, ArgumentHandler.FIXED,
                        ArgumentHandler.DEFAULT_VALUE, "3"));
        assertThat(names(plugin(ArgumentHandler.class, ValueArgument.ID).settingsForm(P, Map.of()))).hasSize(3);
        assertThat(plugin(ArgumentHandler.class, TermArgument.ID).settingsValues(P, Map.of()))
                .containsEntry(ArgumentHandler.DEFAULT_ACTION, ArgumentHandler.IGNORE);
        assertThat(names(plugin(ArgumentHandler.class, TermArgument.ID).settingsForm(P, Map.of()))).hasSize(2);
        assertThat(plugin(RelationshipHandler.class, ReferenceRelationship.ID).settingsValues(P, Map.of(
                P + ReferenceRelationship.TARGET_TYPE, "media"))).containsEntry(ReferenceRelationship.TARGET_TYPE,
                "media");
        assertThat(plugin(RelationshipHandler.class, ReferenceRelationship.ID).settingsValues(P, Map.of())).isEmpty();
        assertThat(names(plugin(RelationshipHandler.class, ReferenceRelationship.ID).settingsForm(P, Map.of())))
                .hasSize(1);
    }

    @Test
    void pagersStylesRowsAndAccessTakeTheirSettings() {
        for (String id : List.of(FullPager.ID, MiniPager.ID, SomePager.ID)) {
            assertThat(plugin(PagerPlugin.class, id).settingsValues(P, Map.of(P + PagerPlugin.ITEMS_PER_PAGE, "5",
                    P + PagerPlugin.OFFSET, "x"))).isEqualTo(Map.of(PagerPlugin.ITEMS_PER_PAGE, 5,
                    PagerPlugin.OFFSET, 0));
            assertThat(names(plugin(PagerPlugin.class, id).settingsForm(P, Map.of()))).hasSize(2);
        }
        assertThat(plugin(PagerPlugin.class, NonePager.ID).settingsValues(P, Map.of(P + PagerPlugin.OFFSET, "2")))
                .isEqualTo(Map.of(PagerPlugin.OFFSET, 2));
        assertThat(names(plugin(PagerPlugin.class, NonePager.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(StylePlugin.class, TableStyle.ID).settingsValues(P, Map.of(P + TableStyle.SORTABLE, TICK)))
                .isEqualTo(Map.of(TableStyle.SORTABLE, true));
        assertThat(names(plugin(StylePlugin.class, TableStyle.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(StylePlugin.class, HtmlListStyle.ID).settingsValues(P, Map.of(P + HtmlListStyle.TYPE,
                "ol"))).isEqualTo(Map.of(HtmlListStyle.TYPE, "ol"));
        assertThat(names(plugin(StylePlugin.class, HtmlListStyle.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(StylePlugin.class, GridStyle.ID).settingsValues(P, Map.of(P + GridStyle.COLUMNS, "3")))
                .isEqualTo(Map.of(GridStyle.COLUMNS, 3));
        assertThat(names(plugin(StylePlugin.class, GridStyle.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(StylePlugin.class, UnformattedStyle.ID).label()).isEqualTo("Unformatted list");
        assertThat(plugin(RowPlugin.class, FieldsRow.ID).settingsValues(P, Map.of())).isEqualTo(Map.of(
                FieldsRow.LABELS, false));
        assertThat(names(plugin(RowPlugin.class, FieldsRow.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(RowPlugin.class, EntityRow.ID).settingsValues(P, Map.of(P + EntityRow.VIEW_MODE, "full")))
                .isEqualTo(Map.of(EntityRow.VIEW_MODE, "full"));
        assertThat(plugin(RowPlugin.class, EntityRow.ID).settingsValues(P, Map.of()))
                .isEqualTo(Map.of(EntityRow.VIEW_MODE, "teaser"));
        assertThat(names(plugin(RowPlugin.class, EntityRow.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(RowPlugin.class, FieldsRow.ID).label()).isEqualTo("Fields");
        assertThat(plugin(RowPlugin.class, EntityRow.ID).label()).isEqualTo("Rendered entity");
        assertThat(plugin(AccessPlugin.class, PermissionAccess.ID).settingsValues(P, Map.of(P
                + PermissionAccess.PERMISSION, "see"))).isEqualTo(Map.of(PermissionAccess.PERMISSION, "see"));
        assertThat(names(plugin(AccessPlugin.class, PermissionAccess.ID).settingsForm(P, Map.of()))).hasSize(1);
        assertThat(plugin(AccessPlugin.class, NoneAccess.ID).settingsForm(P, Map.of())).isEmpty();
    }

    @Test
    void sharedSettingsElementsReadTheirValues() {
        FormElement ticked = Settings.checkbox(P, "on", "On", Map.of("on", "true"), false);
        FormElement fallback = Settings.checkbox(P, "on", "On", Map.of(), true);

        assertThat(ticked.value()).isEqualTo(true);
        assertThat(fallback.value()).isEqualTo(true);
        assertThat(Settings.number(P, "n", "N", Map.of("n", 4), 1).value()).isEqualTo("4");
        assertThat(Settings.whole(P, "n", Map.of(P + "n", "12"), 1)).isEqualTo(12);
    }
}
