package dev.springdrop.kernel.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.views.blocks.RequestInput;
import dev.springdrop.kernel.views.handlers.LinksField;
import dev.springdrop.kernel.views.handlers.NonePager;
import dev.springdrop.kernel.views.handlers.PermissionAccess;
import dev.springdrop.kernel.views.handlers.RoleFilter;
import dev.springdrop.kernel.views.handlers.TermArgument;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.HtmlListStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ViewModelTest {

    private static final HandlerConfig LABEL = HandlerConfig.of("label", "entity_label", "label", Map.of());

    private static ViewConfig view(ViewOptions defaults, ViewDisplay... more) {
        List<ViewDisplay> displays = new ArrayList<>(List.of(new ViewDisplay(ViewDisplay.DEFAULT,
                ViewDisplay.DEFAULT, "", defaults, Map.of())));
        displays.addAll(List.of(more));
        return new ViewConfig("v", "V", "", "node", displays);
    }

    private static ViewOptions full() {
        return new ViewOptions(List.of(LABEL), List.of(), List.of(), List.of(), List.of(), PluginConfig.of("full"),
                PluginConfig.of("default"), PluginConfig.of("fields"), PluginConfig.of("none"));
    }

    @Test
    void everySectionAndKindOfPluginIsReadAndWrittenByName() {
        ViewOptions options = ViewOptions.INHERIT;
        for (String section : ViewEditing.SECTIONS) {
            options = ViewEditing.withSection(options, section, List.of(LABEL));
            assertThat(ViewEditing.section(options, section)).as(section).containsExactly(LABEL);
        }
        for (String kind : ViewEditing.KINDS) {
            options = ViewEditing.withPlugin(options, kind, PluginConfig.of(kind));
            assertThat(ViewEditing.plugin(options, kind)).as(kind).isEqualTo(PluginConfig.of(kind));
        }
    }

    @Test
    void aDisplayEditsTheDefaultsPartsUntilItTakesItsOwn() {
        ViewConfig view = view(full(), new ViewDisplay("page_1", ViewDisplay.PAGE, "", ViewOptions.INHERIT, Map.of()));

        ViewConfig shared = ViewEditing.editSection(view, "page_1", ViewEditing.FILTERS,
                current -> List.of(LABEL));
        ViewConfig own = ViewEditing.editSection(ViewEditing.toggleOverride(view, "page_1", ViewEditing.SORTS),
                "page_1", ViewEditing.SORTS, current -> List.of(LABEL));
        ViewConfig plugin = ViewEditing.editPlugin(view, "page_1", ViewEditing.ROW, PluginConfig.of("entity"));
        ViewConfig ownAccess = ViewEditing.editPlugin(ViewEditing.toggleOverride(view, "page_1",
                ViewEditing.ACCESS), "page_1", ViewEditing.ACCESS, PluginConfig.of("permission"));

        assertThat(shared.defaultDisplay().overrides().filters()).containsExactly(LABEL);
        assertThat(own.display("page_1").orElseThrow().overrides().sorts()).containsExactly(LABEL);
        assertThat(own.defaultDisplay().overrides().sorts()).isEmpty();
        assertThat(plugin.defaultDisplay().overrides().row()).isEqualTo(PluginConfig.of("entity"));
        assertThat(ownAccess.display("page_1").orElseThrow().overrides().access())
                .isEqualTo(PluginConfig.of("permission"));
        assertThat(ViewEditing.overrides(view, ViewDisplay.DEFAULT, ViewEditing.FIELDS)).isTrue();
    }

    @Test
    void aSectionTheDefaultsLeaveEmptyStartsEmpty() {
        ViewOptions empty = new ViewOptions(null, null, null, null, null, null, null, null, null);
        ViewConfig view = view(empty, new ViewDisplay("page_1", ViewDisplay.PAGE, "", ViewOptions.INHERIT, Map.of()));

        ViewConfig edited = ViewEditing.editSection(view, ViewDisplay.DEFAULT, ViewEditing.FIELDS,
                current -> current);
        ViewConfig copied = ViewEditing.toggleOverride(view, "page_1", ViewEditing.FIELDS);

        assertThat(edited.defaultDisplay().overrides().fields()).isEmpty();
        assertThat(copied.display("page_1").orElseThrow().overrides().fields()).isEmpty();
    }

    @Test
    void aViewMissingItsDefaultDisplayIsRefused() {
        ViewConfig broken = new ViewConfig("v", "V", "", "node", List.of());

        assertThatThrownBy(broken::defaultDisplay)
                .isInstanceOf(IllegalStateException.class);
        assertThat(view(full()).options("missing")).isEqualTo(full());
    }

    @Test
    void displayPathsMatchPartForPart() {
        assertThat(ViewPaths.arguments("/news/%", "/news/7")).contains(List.of("7"));
        assertThat(ViewPaths.arguments("", "/news")).isEmpty();
        assertThat(ViewPaths.arguments("news", "/news")).isEmpty();
        assertThat(ViewPaths.arguments("/news", "/blog")).isEmpty();
        assertThat(ViewPaths.address(new ViewDisplay("p", ViewDisplay.PAGE, "", ViewOptions.INHERIT,
                Map.of(ViewDisplay.PATH, "/%")))).isEqualTo("/");
    }

    @Test
    void flagsReadTrueAsABooleanOrAsText() {
        assertThat(HandlerConfig.of("x", "p", "y", Map.of("on", "true")).flag("on")).isTrue();
        assertThat(HandlerConfig.of("x", "p", "y", Map.of("on", true)).flag("on")).isTrue();
        assertThat(HandlerConfig.of("x", "p", "y", Map.of("on", "no")).flag("on")).isFalse();
        ViewDisplay display = new ViewDisplay("d", ViewDisplay.PAGE, "", ViewOptions.INHERIT, Map.of("a", "true",
                "b", true, "c", 1));
        assertThat(List.of(display.flag("a"), display.flag("b"), display.flag("c"))).containsExactly(true, true,
                false);
    }

    @Test
    void linksAreDrawnForEachResultFromTheirSettings() {
        LinksField links = new LinksField();
        ResultRow row = new ResultRow(EntityData.of("user", 7L, null, "edith", Map.of()), Map.of());

        String drawn = links.render(row, HandlerConfig.of("l", LinksField.ID, "id", Map.of(LinksField.LINKS, List.of(
                Map.of("label", "Edit", "path", "/people/{id}/edit"), "stray",
                Map.of("label", "Close", "path", "/people/{id}/close", "style", "danger")))));

        assertThat(Jsoup.parseBodyFragment(drawn).select("a").eachAttr("href")).containsExactly("/people/7/edit",
                "/people/7/close");
        assertThat(Jsoup.parseBodyFragment(drawn).select("a.btn-danger").text()).isEqualTo("Close");
        assertThat(links.render(row, HandlerConfig.of("l", LinksField.ID, "id", Map.of(LinksField.LINKS, "none"))))
                .isEmpty();
        assertThat(links.sortable()).isFalse();
    }

    @Test
    void theFirstAccountMaySeeAViewWhateverItsPermission() {
        AccountPrincipal first = new AccountPrincipal(1L, "admin", "", true, List.of());

        assertThat(new PermissionAccess().allows(new PluginConfig(PermissionAccess.ID, Map.of(
                PermissionAccess.PERMISSION, "rare")), new UsernamePasswordAuthenticationToken(first, null,
                List.of()))).isTrue();
    }

    @Test
    void aFieldsRowLabelsFieldsWhenToldAndWithoutFieldsDrawsNothing() {
        FieldsRow row = new FieldsRow();
        ResultRow result = new ResultRow(EntityData.of("node", 1L, "page", "Hours", Map.of()), Map.of());

        String labelled = row.render(result, full(), new PluginConfig(FieldsRow.ID, Map.of(FieldsRow.LABELS, "true")),
                (each, field) -> "Hours", field -> "Title");

        assertThat(labelled).contains("Title: ");
        assertThat(row.render(result, ViewOptions.INHERIT, PluginConfig.of(FieldsRow.ID), (each, field) -> "x",
                field -> "y")).isEmpty();
    }

    @Test
    void anHtmlListIsBulletedUnlessNumbered() {
        String drawn = new HtmlListStyle().render(new StyleContext(List.of(new ResultRow(EntityData.of("node", 1L,
                "page", "Hours", Map.of()), Map.of())), List.of(), PluginConfig.of(HtmlListStyle.ID),
                each -> "Hours", (each, field) -> "", field -> "", field -> Optional.empty()));

        assertThat(drawn).startsWith("<ul");
    }

    @Test
    void outsideARequestThereIsNoInputAndTheNonePagerHasNoPages() {
        assertThat(new RequestInput().current()).isEmpty();
        assertThat(new NonePager().pages()).isFalse();
    }

    @Test
    void aRoleFilterGivenSomethingThatIsNotARoleFindsNothing() {
        RoleFilter filter = new RoleFilter(mock(RoleManager.class));
        HandlerConfig roles = HandlerConfig.of("roles", RoleFilter.ID, "roles", Map.of("label", "Group"));

        assertThat(filter.condition(roles, "Not A Role")).contains(Condition.in("roles", List.of()));
        assertThat(filter.exposedElement(roles, "").label()).isEqualTo("Group");
    }

    @Test
    void aTermArgumentGivenSomethingThatIsNotAnIdFindsNothing() {
        assertThat(new TermArgument(mock(FieldConfigManager.class)).condition(HandlerConfig.of("term",
                TermArgument.ID, "node", Map.of()), "apples")).isEmpty();
    }
}
