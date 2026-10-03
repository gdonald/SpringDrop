package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQuery;
import dev.springdrop.kernel.entity.query.EntityQueryAccessFilter;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Turns a view's options into an entity query and assembles what it finds.
 *
 * <p>The query carries the base entity type's access tag, so the type's access
 * rules narrow it, and each result the reader may not view is left out, unless
 * the default display turns that check off. A
 * filter or contextual filter on a relationship's entity narrows the base
 * results to those whose relationship reaches an entity matching it. Sorts
 * order by the base entity's properties: first by the field the reader sorted a
 * table by, given as {@code order} and {@code sort}, then by the orders the
 * base entity's filters give, such as a search's ranking, then by the exposed sort
 * the reader chose, given as {@code sort_by} and {@code sort_order}, or the
 * first exposed sort, then by the rest in their order. A contextual filter given no value
 * does what its default action says, and one given a value the property cannot
 * hold finds nothing. Handlers whose plugin the site no longer has are passed
 * over.
 */
@Component
public class ViewExecutor {

    /** The field a table is sorted by. */
    public static final String ORDER = "order";

    /** {@code asc} or {@code desc}, for the field a table is sorted by. */
    public static final String SORT = "sort";

    /** The exposed sort the reader chose. */
    public static final String SORT_BY = "sort_by";

    /** {@code asc} or {@code desc}, for the exposed sort. */
    public static final String SORT_ORDER = "sort_order";

    /** Marks a sort the reader chooses. */
    public static final String EXPOSED = "exposed";

    private final EntityQueryExecutor queries;
    private final EntityCrudService entities;
    private final EntityAccessManager access;
    private final PluginRegistry registry;

    public ViewExecutor(EntityQueryExecutor queries, EntityCrudService entities, EntityAccessManager access,
            PluginRegistry registry) {
        this.queries = queries;
        this.entities = entities;
        this.access = access;
        this.registry = registry;
    }

    /** Whether someone may see a display of the view, by its access plugin. */
    public boolean mayAccess(ViewConfig view, String displayId, Authentication authentication) {
        PluginConfig config = view.options(displayId).access();
        PluginManager<AccessPlugin> plugins = registry.managerFor(AccessPlugin.class);
        return config == null || !plugins.has(config.plugin())
                || plugins.get(config.plugin()).allows(config, authentication);
    }

    /**
     * Runs a display of the view.
     *
     * @param arguments the contextual filters' values, in their order
     * @param exposed   the exposed filters' values, by identifier, with the sorts the reader chose
     * @param page      the page asked for, from one
     */
    public ViewResult execute(ViewConfig view, String displayId, List<String> arguments, Map<String, String> exposed,
            int page) {
        ViewOptions options = view.options(displayId);
        Optional<List<Condition>> conditions = conditions(view, options, arguments, exposed);
        if (conditions.isEmpty()) {
            return new ViewResult(List.of(), 0, 1, 1, options);
        }
        List<Sort> ranking = filterSorts(options, exposed);
        long total = query(view, options, conditions.get(), ranking, exposed).count();

        PluginManager<PagerPlugin> pagers = registry.managerFor(PagerPlugin.class);
        PluginConfig pagerConfig = options.pager() == null ? PluginConfig.of("none") : options.pager();
        PagerPlugin pager = pagers.has(pagerConfig.plugin()) ? pagers.get(pagerConfig.plugin()) : pagers.get("none");
        int perPage = pager.itemsPerPage(pagerConfig);
        int offset = pager.offset(pagerConfig);
        long listed = Math.max(0, total - offset);
        int totalPages = (!pager.pages() || perPage == 0) ? 1 : (int) Math.max(1, (listed + perPage - 1) / perPage);
        int shown = Math.min(Math.max(1, page), totalPages);
        int limit = (perPage == 0) ? (int) Math.max(listed, 1) : perPage;

        EntityQuery found = query(view, options, conditions.get(), ranking, exposed).range(offset + (shown - 1) * perPage, limit);
        boolean checkEach = !"false".equals(String.valueOf(view.defaultDisplay().settings()
                .getOrDefault(ViewDisplay.ENTITY_ACCESS, true)));
        List<ResultRow> rows = new ArrayList<>();
        for (Object id : found.ids()) {
            entities.load(view.baseEntityType(), id)
                    .filter(entity -> !checkEach || access.may(view.baseEntityType(), entity, EntityAccessHandler.VIEW))
                    .ifPresent(entity -> rows.add(new ResultRow(entity, related(view, options, entity), exposed)));
        }
        return new ViewResult(rows, total, shown, totalPages, options);
    }

    private EntityQuery query(ViewConfig view, ViewOptions options, List<Condition> conditions, List<Sort> ranking,
            Map<String, String> input) {
        EntityQuery query = queries.query(view.baseEntityType())
                .accessTag(EntityQueryAccessFilter.accessTagFor(view.baseEntityType()));
        conditions.forEach(query::condition);
        clickSort(options, input).ifPresent(query::sort);
        ranking.forEach(query::sort);
        PluginManager<SortHandler> sorts = registry.managerFor(SortHandler.class);
        List<HandlerConfig> usable = listed(options.sorts()).stream()
                .filter(sort -> HandlerConfig.BASE.equals(sort.relationship()) && sorts.has(sort.plugin()))
                .toList();
        List<HandlerConfig> exposedSorts = usable.stream().filter(sort -> sort.flag(EXPOSED)).toList();
        if (!exposedSorts.isEmpty()) {
            HandlerConfig chosen = exposedSorts.stream()
                    .filter(sort -> sort.id().equals(input.getOrDefault(SORT_BY, ""))).findFirst()
                    .orElse(exposedSorts.getFirst());
            String order = input.getOrDefault(SORT_ORDER, "");
            Map<String, Object> settings = new LinkedHashMap<>(chosen.settings());
            if (order.equals("asc") || order.equals("desc")) {
                settings.put(SortHandler.ORDER, order);
            }
            query.sort(sorts.get(chosen.plugin()).sort(new HandlerConfig(chosen.id(), chosen.plugin(),
                    chosen.relationship(), chosen.property(), settings)));
        }
        usable.stream().filter(sort -> !sort.flag(EXPOSED))
                .forEach(sort -> query.sort(sorts.get(sort.plugin()).sort(sort)));
        return query;
    }

    /** The orders the view's own entity's filters put the results in, such as a search's ranking. */
    private List<Sort> filterSorts(ViewOptions options, Map<String, String> exposed) {
        PluginManager<FilterHandler> filters = registry.managerFor(FilterHandler.class);
        List<Sort> sorts = new ArrayList<>();
        for (HandlerConfig filter : listed(options.filters())) {
            if (HandlerConfig.BASE.equals(filter.relationship()) && filters.has(filter.plugin())) {
                filters.get(filter.plugin()).sort(filter, filterValue(filter, exposed)).ifPresent(sorts::add);
            }
        }
        return sorts;
    }

    private static String filterValue(HandlerConfig filter, Map<String, String> exposed) {
        return filter.flag(FilterHandler.EXPOSED) ? exposed.getOrDefault(FilterHandler.identifier(filter), "")
                : filter.text(FilterHandler.VALUE);
    }

    /** The sort by the field a table was sorted by, for a field of the view's own entity. */
    private static Optional<Sort> clickSort(ViewOptions options, Map<String, String> input) {
        String order = input.getOrDefault(ORDER, "");
        return listed(options.fields()).stream()
                .filter(field -> field.id().equals(order) && HandlerConfig.BASE.equals(field.relationship()))
                .findFirst()
                .map(field -> "desc".equals(input.get(SORT)) ? Sort.descending(field.property())
                        : Sort.ascending(field.property()));
    }

    /** The conditions the filters and contextual filters add, or nothing when the view finds nothing. */
    private Optional<List<Condition>> conditions(ViewConfig view, ViewOptions options, List<String> arguments,
            Map<String, String> exposed) {
        List<Condition> conditions = new ArrayList<>();
        PluginManager<FilterHandler> filters = registry.managerFor(FilterHandler.class);
        for (HandlerConfig filter : listed(options.filters())) {
            if (!filters.has(filter.plugin())) {
                continue;
            }
            filters.get(filter.plugin()).condition(filter, filterValue(filter, exposed))
                    .ifPresent(condition -> conditions.add(through(view, options, filter, condition)));
        }
        PluginManager<ArgumentHandler> handlers = registry.managerFor(ArgumentHandler.class);
        List<HandlerConfig> argumentConfigs = listed(options.arguments());
        for (int position = 0; position < argumentConfigs.size(); position++) {
            HandlerConfig argument = argumentConfigs.get(position);
            if (!handlers.has(argument.plugin())) {
                continue;
            }
            String value = (position < arguments.size()) ? arguments.get(position).strip() : "";
            if (value.isEmpty()) {
                String action = argument.text(ArgumentHandler.DEFAULT_ACTION);
                if (action.equals(ArgumentHandler.EMPTY)) {
                    return Optional.empty();
                }
                if (!action.equals(ArgumentHandler.FIXED)) {
                    continue;
                }
                value = argument.text(ArgumentHandler.DEFAULT_VALUE);
            }
            Optional<Condition> condition = handlers.get(argument.plugin()).condition(argument, value);
            if (condition.isEmpty()) {
                return Optional.empty();
            }
            conditions.add(through(view, options, argument, condition.get()));
        }
        return Optional.of(conditions);
    }

    /**
     * The condition as it applies to the base entity: as it is, or for one on a
     * relationship's entity, that the relationship reaches an entity matching it.
     */
    private Condition through(ViewConfig view, ViewOptions options, HandlerConfig handler, Condition condition) {
        Optional<HandlerConfig> relationship = relationship(options, handler.relationship());
        PluginManager<RelationshipHandler> relationships = registry.managerFor(RelationshipHandler.class);
        if (relationship.isEmpty() || !relationships.has(relationship.get().plugin())) {
            return condition;
        }
        String targetType = relationships.get(relationship.get().plugin())
                .targetType(view.baseEntityType(), relationship.get());
        List<Object> reached = queries.query(targetType).accessTag(EntityQueryAccessFilter.accessTagFor(targetType))
                .condition(condition).ids();
        return Condition.in(relationship.get().property(), reached);
    }

    private static Optional<HandlerConfig> relationship(ViewOptions options, String id) {
        return listed(options.relationships()).stream().filter(each -> each.id().equals(id)).findFirst();
    }

    /** The entities each relationship reaches from one result, those the reader may view. */
    private Map<String, EntityData> related(ViewConfig view, ViewOptions options, EntityData entity) {
        Map<String, EntityData> related = new LinkedHashMap<>();
        PluginManager<RelationshipHandler> relationships = registry.managerFor(RelationshipHandler.class);
        for (HandlerConfig relationship : listed(options.relationships())) {
            if (!relationships.has(relationship.plugin())) {
                continue;
            }
            RelationshipHandler handler = relationships.get(relationship.plugin());
            String targetType = handler.targetType(view.baseEntityType(), relationship);
            handler.targetId(entity, relationship)
                    .flatMap(id -> entities.load(targetType, id))
                    .filter(target -> access.may(targetType, target, EntityAccessHandler.VIEW))
                    .ifPresent(target -> related.put(relationship.id(), target));
        }
        return related;
    }

    private static List<HandlerConfig> listed(List<HandlerConfig> handlers) {
        return (handlers == null) ? List.of() : handlers;
    }
}
