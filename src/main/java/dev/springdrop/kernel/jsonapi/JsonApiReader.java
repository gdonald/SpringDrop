package dev.springdrop.kernel.jsonapi;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.FieldAccessManager;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQuery;
import dev.springdrop.kernel.entity.query.EntityQueryAccessFilter;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Answers JSON:API reads. A collection is filtered, sorted, and paged in the
 * query, and a resource the person may not view is left out of the page and
 * named under {@code meta.omitted}. Filtering or sorting on a field the person
 * may not view is refused. Included resources follow the relationships the
 * {@code include} parameter names, as deep as its dotted paths go.
 */
@Component
public class JsonApiReader {

    static final String OMITTED_DETAIL = "Some resources have been omitted because of insufficient authorization.";

    private final EntityTypeManager entityTypes;
    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityAccessManager access;
    private final FieldAccessManager fieldAccess;
    private final JsonApiSchema schema;
    private final JsonApiNormalizer normalizer;
    private final JsonApiResourceTypes resourceTypes;
    private final ConfigStore config;

    public JsonApiReader(EntityTypeManager entityTypes, EntityCrudService entities, EntityQueryExecutor queries,
            EntityAccessManager access, FieldAccessManager fieldAccess, JsonApiSchema schema,
            JsonApiNormalizer normalizer, JsonApiResourceTypes resourceTypes, ConfigStore config) {
        this.entityTypes = entityTypes;
        this.entities = entities;
        this.queries = queries;
        this.access = access;
        this.fieldAccess = fieldAccess;
        this.schema = schema;
        this.normalizer = normalizer;
        this.resourceTypes = resourceTypes;
        this.config = config;
    }

    /** The links to every resource type's collection. */
    public Map<String, Object> entryPoint(String base) {
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", JsonApiDocuments.link(base + JsonApiResourceType.BASE_PATH));
        resourceTypes.all().forEach(type -> links.put(type.name(), JsonApiDocuments.link(base + type.path())));
        return JsonApiDocuments.document(List.of(), List.of(), Map.of(), links);
    }

    /**
     * One page of a collection. The page links are made by {@code pageLink},
     * which gives the address of the page starting at an offset.
     */
    public Map<String, Object> collection(JsonApiResourceType resourceType, JsonApiQuery query, String base,
            String self, IntFunction<String> pageLink, Authentication who) {
        Omitted omitted = new Omitted();
        List<Map<String, Object>> data = new ArrayList<>();
        List<Map<String, Object>> included = List.of();
        long count;
        if (resourceType.config()) {
            refuseIncludes(resourceType, query);
            List<ConfigEntity> matching = configEntities(resourceType, query);
            count = matching.size();
            for (ConfigEntity each : matching.subList(Math.min(query.offset(), matching.size()),
                    Math.min(query.offset() + query.limit(), matching.size()))) {
                if (access.check(resourceType.entityType(), each.values(), EntityAccessHandler.VIEW, who).allowed()) {
                    data.add(normalizer.configResource(resourceType, each.id(), each.values(),
                            query.fieldsOf(resourceType.name()), base));
                } else {
                    omitted.add(Map.of("type", resourceType.name(), "id", each.id()));
                }
            }
        } else {
            EntityType type = entityTypes.require(resourceType.entityType());
            query.includes().forEach(path -> validateInclude(type, path));
            List<Condition> conditions = conditions(type, resourceType, query, who);
            EntityQuery page = query(type, conditions).range(query.offset(), query.limit());
            sorts(type, resourceType, query, who).forEach(page::sort);
            List<EntityData> shown = new ArrayList<>();
            for (Object id : page.ids()) {
                entities.load(type.id(), id).ifPresent(entity -> {
                    if (access.check(type.id(), entity, EntityAccessHandler.VIEW, who).allowed()) {
                        shown.add(entity);
                    } else {
                        omitted.add(normalizer.identifier(entity));
                    }
                });
            }
            count = query(type, conditions).count();
            shown.forEach(entity -> data.add(normalizer.resource(entity, query.fieldsOf(resourceType.name()), base,
                    who)));
            included = included(shown, query, base, who, omitted);
        }
        Map<String, Object> links = new LinkedHashMap<>();
        links.put("self", JsonApiDocuments.link(self));
        if (query.offset() > 0) {
            links.put("prev", JsonApiDocuments.link(pageLink.apply(Math.max(0, query.offset() - query.limit()))));
        }
        if (query.offset() + query.limit() < count) {
            links.put("next", JsonApiDocuments.link(pageLink.apply(query.offset() + query.limit())));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("count", count);
        omitted.into(meta);
        return JsonApiDocuments.document(data, included, meta, links);
    }

    /** One resource, with the resources its {@code include} parameter names. */
    public Map<String, Object> individual(JsonApiResourceType resourceType, String id, JsonApiQuery query,
            String base, String self, Authentication who) {
        if (resourceType.config()) {
            refuseIncludes(resourceType, query);
            EntityType type = entityTypes.require(resourceType.entityType());
            Map<String, Object> values = entityTypes.storageFor(type.id()).load(type, id)
                    .orElseThrow(() -> notFound(resourceType, id));
            if (!access.check(type.id(), values, EntityAccessHandler.VIEW, who).allowed()) {
                throw new JsonApiException(HttpStatus.FORBIDDEN, "You may not view this " + resourceType.name() + ".");
            }
            return JsonApiDocuments.document(normalizer.configResource(resourceType, id, values,
                    query.fieldsOf(resourceType.name()), base), List.of(), Map.of(),
                    Map.of("self", JsonApiDocuments.link(self)));
        }
        EntityData entity = find(resourceType, id);
        if (!access.check(entity.entityType(), entity, EntityAccessHandler.VIEW, who).allowed()) {
            throw new JsonApiException(HttpStatus.FORBIDDEN, "You may not view this " + resourceType.name() + ".");
        }
        return document(entity, query, base, self, who);
    }

    /** A document holding one content entity the person may view. */
    public Map<String, Object> document(EntityData entity, JsonApiQuery query, String base, String self,
            Authentication who) {
        query.includes().forEach(path -> validateInclude(entityTypes.require(entity.entityType()), path));
        Omitted omitted = new Omitted();
        List<Map<String, Object>> included = included(List.of(entity), query, base, who, omitted);
        Map<String, Object> meta = new LinkedHashMap<>();
        omitted.into(meta);
        return JsonApiDocuments.document(normalizer.resource(entity,
                query.fieldsOf(resourceTypes.of(entity).name()), base, who), included, meta,
                Map.of("self", JsonApiDocuments.link(self)));
    }

    /** The content entity of the resource type with the uuid, or a 404. */
    public EntityData find(JsonApiResourceType resourceType, String id) {
        EntityType type = entityTypes.require(resourceType.entityType());
        List<Condition> conditions = new ArrayList<>(bundleCondition(type, resourceType));
        Optional<UUID> uuid = uuid(id);
        if (uuid.isEmpty()) {
            throw notFound(resourceType, id);
        }
        conditions.add(Condition.equal(type.keys().uuid(), uuid.get()));
        return query(type, conditions).ids().stream().findFirst().flatMap(found -> entities.load(type.id(), found))
                .orElseThrow(() -> notFound(resourceType, id));
    }

    /** The ids of the entities of a type with the uuids, leaving out uuids nothing has. */
    public List<Object> idsOf(String entityType, List<String> uuids) {
        EntityType type = entityTypes.require(entityType);
        List<UUID> parsed = uuids.stream().map(JsonApiReader::uuid).flatMap(Optional::stream).toList();
        return parsed.isEmpty() ? List.of()
                : queries.query(type.id()).condition(Condition.in(type.keys().uuid(), parsed)).ids();
    }

    private static Optional<UUID> uuid(String id) {
        try {
            return Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    private static JsonApiException notFound(JsonApiResourceType resourceType, String id) {
        return new JsonApiException(HttpStatus.NOT_FOUND, "There is no " + resourceType.name() + " " + id + ".");
    }

    private EntityQuery query(EntityType type, List<Condition> conditions) {
        EntityQuery query = queries.query(type.id()).accessTag(EntityQueryAccessFilter.accessTagFor(type.id()));
        conditions.forEach(query::condition);
        return query;
    }

    private static List<Condition> bundleCondition(EntityType type, JsonApiResourceType resourceType) {
        return type.keys().bundle() == null ? List.of()
                : List.of(Condition.equal(type.keys().bundle(), resourceType.bundle()));
    }

    private List<Condition> conditions(EntityType type, JsonApiResourceType resourceType, JsonApiQuery query,
            Authentication who) {
        List<Condition> conditions = new ArrayList<>(bundleCondition(type, resourceType));
        if (query.filter() != null) {
            conditions.add(condition(type, resourceType.bundle(), query.filter(), who));
        }
        return conditions;
    }

    private Condition condition(EntityType type, String bundle, JsonApiFilter filter, Authentication who) {
        return switch (filter) {
            case JsonApiFilter.Group group -> new Condition.Group(group.conjunction().equals(JsonApiFilter.OR)
                    ? Condition.Conjunction.OR : Condition.Conjunction.AND,
                    group.members().stream().map(member -> condition(type, bundle, member, who)).toList());
            case JsonApiFilter.Match match -> match(type, bundle, match, who);
        };
    }

    private Condition match(EntityType type, String bundle, JsonApiFilter.Match match, Authentication who) {
        String[] segments = match.path().split("\\.", 2);
        String target = schema.relationships(type, bundle).get(segments[0]);
        if (target != null) {
            if (segments.length != 2 || !segments[1].equals("id")) {
                throw new JsonApiException(HttpStatus.BAD_REQUEST, "The relationship " + segments[0]
                        + " is filtered on by its id, as " + segments[0] + ".id.");
            }
            if (!match.operator().equals(JsonApiFilter.EQUAL) && !match.operator().equals(JsonApiFilter.IN)) {
                throw new JsonApiException(HttpStatus.BAD_REQUEST, "The relationship " + segments[0]
                        + " is filtered on with = or IN.");
            }
            viewable(type, bundle, segments[0], who);
            List<String> uuids = match.value() instanceof List<?> many
                    ? many.stream().map(String::valueOf).toList() : List.of(String.valueOf(match.value()));
            return Condition.in(segments[0], idsOf(target, uuids));
        }
        String property = property(type, bundle, match.path());
        viewable(type, bundle, property, who);
        Object value = switch (match.operator()) {
            case JsonApiFilter.CONTAINS -> match.value();
            case JsonApiFilter.IN -> ((List<?>) match.value()).stream()
                    .map(each -> schema.filterValue(type, property, String.valueOf(each))).toList();
            default -> schema.filterValue(type, property, String.valueOf(match.value()));
        };
        return new Condition.Comparison(property, switch (match.operator()) {
            case JsonApiFilter.NOT_EQUAL -> Condition.Operator.NOT_EQUAL;
            case JsonApiFilter.GREATER_THAN -> Condition.Operator.GREATER_THAN;
            case JsonApiFilter.LESS_THAN -> Condition.Operator.LESS_THAN;
            case JsonApiFilter.IN -> Condition.Operator.IN;
            case JsonApiFilter.CONTAINS -> Condition.Operator.CONTAINS;
            default -> Condition.Operator.EQUAL;
        }, value);
    }

    private List<Sort> sorts(EntityType type, JsonApiResourceType resourceType, JsonApiQuery query,
            Authentication who) {
        List<Sort> sorts = new ArrayList<>();
        for (JsonApiQuery.SortField sort : query.sorts()) {
            String property = property(type, resourceType.bundle(), sort.path());
            viewable(type, resourceType.bundle(), property, who);
            sorts.add(sort.ascending() ? Sort.ascending(property) : Sort.descending(property));
        }
        sorts.add(Sort.ascending(type.keys().id()));
        return sorts;
    }

    private String property(EntityType type, String bundle, String path) {
        return schema.property(type, bundle, path).orElseThrow(() -> new JsonApiException(HttpStatus.BAD_REQUEST,
                "There is no attribute " + path + " on " + new JsonApiResourceType(type.id(), bundle, false).name() + "."));
    }

    private void viewable(EntityType type, String bundle, String property, Authentication who) {
        EntityData placeholder = EntityData.of(type.id(), null, type.keys().bundle() == null ? null : bundle, "",
                Map.of());
        if (!JsonApiSchema.isKey(type, property)
                && !fieldAccess.may(type, property, FieldAccessRule.VIEW, placeholder, who)) {
            throw new JsonApiException(HttpStatus.FORBIDDEN, "You may not filter or sort on " + property + ".");
        }
    }

    private void validateInclude(EntityType type, List<String> path) {
        EntityType current = type;
        for (String segment : path) {
            String target = schema.relationships(current).get(segment);
            if (target == null) {
                throw new JsonApiException(HttpStatus.BAD_REQUEST, segment + " is not a relationship of "
                        + current.id() + ", so " + String.join(".", path) + " cannot be included.");
            }
            current = entityTypes.require(target);
        }
    }

    private static void refuseIncludes(JsonApiResourceType resourceType, JsonApiQuery query) {
        if (!query.includes().isEmpty()) {
            throw new JsonApiException(HttpStatus.BAD_REQUEST, resourceType.name()
                    + " has no relationships to include.");
        }
    }

    private List<Map<String, Object>> included(List<EntityData> primary, JsonApiQuery query, String base,
            Authentication who, Omitted omitted) {
        Set<String> seen = new HashSet<>();
        primary.forEach(entity -> seen.add(key(entity)));
        List<Map<String, Object>> included = new ArrayList<>();
        for (List<String> path : query.includes()) {
            List<EntityData> frontier = primary;
            for (String segment : path) {
                List<EntityData> next = new ArrayList<>();
                for (EntityData entity : frontier) {
                    EntityType type = entityTypes.require(entity.entityType());
                    if (!fieldAccess.may(type, segment, FieldAccessRule.VIEW, entity, who)) {
                        continue;
                    }
                    for (EntityData target : normalizer.related(entity, segment)) {
                        if (!access.check(target.entityType(), target, EntityAccessHandler.VIEW, who).allowed()) {
                            omitted.add(normalizer.identifier(target));
                            continue;
                        }
                        if (seen.add(key(target))) {
                            included.add(normalizer.resource(target,
                                    query.fieldsOf(resourceTypes.of(target).name()), base, who));
                        }
                        next.add(target);
                    }
                }
                frontier = next;
            }
        }
        return included;
    }

    private static String key(EntityData entity) {
        return entity.entityType() + ":" + entity.id();
    }

    /** A config entity of a collection: its machine name, stored values, and attributes. */
    private record ConfigEntity(String id, Map<String, Object> values, Map<String, Object> attributes) {
    }

    /** The config entities of the type matching the filter, in the order the sort asks for. */
    private List<ConfigEntity> configEntities(JsonApiResourceType resourceType, JsonApiQuery query) {
        EntityType type = entityTypes.require(resourceType.entityType());
        List<ConfigEntity> matching = new ArrayList<>();
        for (String name : config.listNames(type.id())) {
            String id = name.substring(type.id().length() + 1);
            entityTypes.storageFor(type.id()).load(type, id)
                    .map(values -> new ConfigEntity(id, values, normalizer.configAttributes(resourceType, id, values)))
                    .filter(each -> query.filter() == null || matches(each.attributes(), query.filter()))
                    .ifPresent(matching::add);
        }
        Comparator<ConfigEntity> order = Comparator.comparing(ConfigEntity::id);
        for (JsonApiQuery.SortField sort : query.sorts().reversed()) {
            Comparator<ConfigEntity> bySort = (left, right) -> compare(left.attributes().get(sort.path()),
                    right.attributes().get(sort.path()));
            order = (sort.ascending() ? bySort : bySort.reversed()).thenComparing(order);
        }
        matching.sort(order);
        return matching;
    }

    private static boolean matches(Map<String, Object> attributes, JsonApiFilter filter) {
        return switch (filter) {
            case JsonApiFilter.Group group -> group.conjunction().equals(JsonApiFilter.OR)
                    ? group.members().isEmpty() || group.members().stream().anyMatch(each -> matches(attributes, each))
                    : group.members().stream().allMatch(each -> matches(attributes, each));
            case JsonApiFilter.Match match -> matches(attributes.get(match.path()), match);
        };
    }

    private static boolean matches(Object actual, JsonApiFilter.Match match) {
        if (actual == null) {
            return match.operator().equals(JsonApiFilter.NOT_EQUAL);
        }
        String text = String.valueOf(actual);
        return switch (match.operator()) {
            case JsonApiFilter.NOT_EQUAL -> !text.equals(match.value());
            case JsonApiFilter.GREATER_THAN -> compare(actual, match.value()) > 0;
            case JsonApiFilter.LESS_THAN -> compare(actual, match.value()) < 0;
            case JsonApiFilter.IN -> ((List<?>) match.value()).contains(text);
            case JsonApiFilter.CONTAINS -> text.contains(String.valueOf(match.value()));
            default -> text.equals(match.value());
        };
    }

    /** Numbers compare as numbers and anything else as text. A missing value comes last. */
    private static int compare(Object left, Object right) {
        if (left == null || right == null) {
            return left == null ? (right == null ? 0 : 1) : -1;
        }
        Optional<BigDecimal> leftNumber = number(left);
        Optional<BigDecimal> rightNumber = number(right);
        if (leftNumber.isPresent() && rightNumber.isPresent()) {
            return leftNumber.get().compareTo(rightNumber.get());
        }
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static Optional<BigDecimal> number(Object value) {
        try {
            return Optional.of(new BigDecimal(String.valueOf(value)));
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** The resources left out because the person may not view them, each named once. */
    private static final class Omitted {

        private final List<Map<String, Object>> resources = new ArrayList<>();

        void add(Map<String, Object> identifier) {
            if (!resources.contains(identifier)) {
                resources.add(identifier);
            }
        }

        void into(Map<String, Object> meta) {
            if (!resources.isEmpty()) {
                meta.put("omitted", Map.of("detail", OMITTED_DETAIL, "resources", List.copyOf(resources)));
            }
        }
    }
}
