package dev.springdrop.kernel.views;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.theme.Pager;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.UnformattedStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Draws a display of a view: the exposed form, the results through the style
 * and row plugins, the text shown when there are none, and the pager. The
 * whole is one element with an id, which the exposed form replaces in place
 * through htmx, and reloads as a page without it. Links to other pages and
 * sorts keep the reader's other choices.
 */
@Component
public class ViewRenderer {

    /** The page asked for, from one. */
    public static final String PAGE = "page";

    /** The display setting naming the text shown when there are no results. */
    public static final String EMPTY_TEXT = "empty_text";

    private final ViewExecutor executor;
    private final PluginRegistry registry;
    private final FormRenderer forms;

    public ViewRenderer(ViewExecutor executor, PluginRegistry registry, FormRenderer forms) {
        this.executor = executor;
        this.registry = registry;
        this.forms = forms;
    }

    /** What a display drew, and what it found. */
    public record Rendered(String html, ViewResult result) {
    }

    /** The element id a display is drawn in. */
    public static String elementId(ViewConfig view, String displayId) {
        return "view-" + view.id() + "-" + displayId;
    }

    /**
     * Draws a display.
     *
     * @param input       the reader's choices: exposed filters, sorts, and the page
     * @param path        the address the display answers at, which its links lead to
     * @param exposedForm whether to draw the exposed form with the results
     */
    public Rendered render(ViewConfig view, String displayId, List<String> arguments, Map<String, String> input,
            String path, boolean exposedForm) {
        String page = input.getOrDefault(PAGE, "1");
        ViewResult result = executor.execute(view, displayId, arguments, input,
                page.matches("\\d{1,9}") ? Integer.parseInt(page) : 1);
        ViewOptions options = result.options();
        String id = elementId(view, displayId);

        StringBuilder markup = new StringBuilder("<div class=\"view view-" + HtmlUtils.htmlEscape(view.id())
                + " view-display-" + HtmlUtils.htmlEscape(displayId) + "\" id=\"" + HtmlUtils.htmlEscape(id) + "\">");
        if (exposedForm) {
            markup.append(exposedForm(view, displayId, input, path, id));
        }
        if (result.rows().isEmpty()) {
            String empty = view.display(displayId).map(display -> display.text(EMPTY_TEXT)).orElse("");
            if (!empty.isEmpty()) {
                markup.append("<p class=\"text-body-secondary views-empty\">").append(HtmlUtils.htmlEscape(empty))
                        .append("</p>");
            }
        } else {
            markup.append(style(options, result.rows(), input, path));
        }
        pager(options, result, input, path).ifPresent(markup::append);
        return new Rendered(markup.append("</div>").toString(), result);
    }

    private String style(ViewOptions options, List<ResultRow> rows, Map<String, String> input, String path) {
        PluginManager<StylePlugin> styles = registry.managerFor(StylePlugin.class);
        PluginConfig styleConfig = options.style() == null ? PluginConfig.of(UnformattedStyle.ID) : options.style();
        StylePlugin style = styles.has(styleConfig.plugin()) ? styles.get(styleConfig.plugin())
                : styles.get(UnformattedStyle.ID);
        PluginManager<RowPlugin> rowPlugins = registry.managerFor(RowPlugin.class);
        PluginConfig rowConfig = options.row() == null ? PluginConfig.of(FieldsRow.ID) : options.row();
        RowPlugin row = rowPlugins.has(rowConfig.plugin()) ? rowPlugins.get(rowConfig.plugin()) : rowPlugins.get(FieldsRow.ID);
        PluginManager<FieldHandler> fields = registry.managerFor(FieldHandler.class);
        List<HandlerConfig> shown = (options.fields() == null) ? List.of()
                : options.fields().stream().filter(field -> fields.has(field.plugin())).toList();
        ViewOptions drawn = options.withFields(shown);
        return style.render(new StyleContext(rows, shown, styleConfig,
                result -> row.render(result, drawn, rowConfig, this::field, this::heading),
                this::field, this::heading,
                field -> fields.get(field.plugin()).sortable() && HandlerConfig.BASE.equals(field.relationship())
                        ? Optional.of(sortUrl(field, input, path)) : Optional.empty()));
    }

    private String field(ResultRow row, HandlerConfig field) {
        return registry.managerFor(FieldHandler.class).get(field.plugin()).render(row, field);
    }

    private String heading(HandlerConfig field) {
        return registry.managerFor(FieldHandler.class).get(field.plugin()).heading(field);
    }

    /** The address sorting by the field: ascending, or descending when it is sorted ascending already. */
    private static String sortUrl(HandlerConfig field, Map<String, String> input, String path) {
        Map<String, String> query = new LinkedHashMap<>(input);
        boolean ascendingNow = field.id().equals(input.get(ViewExecutor.ORDER))
                && !"desc".equals(input.get(ViewExecutor.SORT));
        query.remove(PAGE);
        query.put(ViewExecutor.ORDER, field.id());
        query.put(ViewExecutor.SORT, ascendingNow ? "desc" : "asc");
        return url(path, query);
    }

    private Optional<String> pager(ViewOptions options, ViewResult result, Map<String, String> input, String path) {
        PluginManager<PagerPlugin> pagers = registry.managerFor(PagerPlugin.class);
        PluginConfig config = options.pager() == null ? PluginConfig.of("none") : options.pager();
        PagerPlugin pager = pagers.has(config.plugin()) ? pagers.get(config.plugin()) : pagers.get("none");
        return pager.links(result.page(), result.totalPages(), number -> {
            Map<String, String> query = new LinkedHashMap<>(input);
            query.put(PAGE, String.valueOf(number));
            return url(path, query);
        }).map(ViewRenderer::pagerMarkup);
    }

    private static String pagerMarkup(Pager pager) {
        StringBuilder markup = new StringBuilder("<nav aria-label=\"Pagination\"><ul class=\"pagination\">");
        markup.append(edge(pager.previousUrl(), "Previous"));
        pager.pages().forEach(page -> markup.append("<li class=\"page-item").append(page.current() ? " active" : "")
                .append("\"><a class=\"page-link\" href=\"").append(HtmlUtils.htmlEscape(page.url())).append("\">")
                .append(page.number()).append("</a></li>"));
        markup.append(edge(pager.nextUrl(), "Next"));
        return markup.append("</ul></nav>").toString();
    }

    private static String edge(Optional<String> url, String label) {
        return "<li class=\"page-item" + (url.isPresent() ? "" : " disabled") + "\"><a class=\"page-link\" href=\""
                + HtmlUtils.htmlEscape(url.orElse("#")) + "\">" + label + "</a></li>";
    }

    /**
     * The form the reader gives the exposed filters and sorts with, or nothing
     * when the display has none. It sends the reader to the display's address,
     * and with htmx, replaces the display in place.
     */
    public String exposedForm(ViewConfig view, String displayId, Map<String, String> input, String path,
            String target) {
        ViewOptions options = view.options(displayId);
        PluginManager<FilterHandler> filters = registry.managerFor(FilterHandler.class);
        List<FormElement> controls = new ArrayList<>();
        for (HandlerConfig filter : options.filters() == null ? List.<HandlerConfig>of() : options.filters()) {
            if (filter.flag(FilterHandler.EXPOSED) && filters.has(filter.plugin())) {
                controls.add(filters.get(filter.plugin()).exposedElement(filter,
                        input.getOrDefault(FilterHandler.identifier(filter), "")));
            }
        }
        List<HandlerConfig> exposedSorts = (options.sorts() == null) ? List.of()
                : options.sorts().stream().filter(sort -> sort.flag(ViewExecutor.EXPOSED)).toList();
        if (!exposedSorts.isEmpty()) {
            controls.add(FormElement.of(ElementType.SELECT, ViewExecutor.SORT_BY).label("Sort by")
                    .value(input.getOrDefault(ViewExecutor.SORT_BY, ""))
                    .options(exposedSorts.stream().map(sort -> new SelectOption(sort.id(),
                            sort.text(FieldHandler.LABEL).isEmpty() ? sort.property() : sort.text(FieldHandler.LABEL)))
                            .toList()));
            controls.add(FormElement.of(ElementType.SELECT, ViewExecutor.SORT_ORDER).label("Order")
                    .value(input.getOrDefault(ViewExecutor.SORT_ORDER, ""))
                    .options(List.of(new SelectOption("asc", "Ascending"), new SelectOption("desc", "Descending"))));
        }
        if (controls.isEmpty()) {
            return "";
        }
        String selector = "#" + HtmlUtils.htmlEscape(target);
        StringBuilder markup = new StringBuilder("<form class=\"views-exposed-form row g-2 align-items-end mb-3\" "
                + "method=\"get\" action=\"" + HtmlUtils.htmlEscape(path) + "\" hx-get=\"" + HtmlUtils.htmlEscape(path)
                + "\" hx-target=\"" + selector + "\" hx-select=\"" + selector + "\" hx-swap=\"outerHTML\" "
                + "hx-push-url=\"true\">");
        controls.forEach(control -> markup.append("<div class=\"col-auto\">").append(forms.render(control))
                .append("</div>"));
        return markup.append("<div class=\"col-auto mb-3\"><button class=\"btn btn-primary\" type=\"submit\">"
                + "Apply</button></div></form>").toString();
    }

    private static String url(String path, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        query.forEach(builder::queryParam);
        return builder.build().encode().toUriString();
    }
}
