package dev.springdrop.kernel.jsonapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * What a request's query parameters ask of a collection: the filter, the sort,
 * the page, the fields of each resource type to send, and the relationships to
 * include. A parameter that cannot be read is refused with a 400.
 *
 * <p>A filter is {@code filter[path]=value}, {@code filter[path][value]} with
 * {@code filter[path][operator]}, or a named condition
 * {@code filter[name][condition][path|operator|value]}. Named groups
 * {@code filter[name][group][conjunction]} take conditions and other groups
 * through {@code memberOf}. A page is at most {@value #MAX_LIMIT} resources.
 */
public record JsonApiQuery(JsonApiFilter filter, List<SortField> sorts, int offset, int limit,
        Map<String, Set<String>> fields, List<List<String>> includes) {

    public static final int MAX_LIMIT = 50;

    private static final Pattern FILTER_KEY = Pattern.compile("filter((?:\\[[^\\[\\]]*])+)");

    private static final Pattern SEGMENT = Pattern.compile("\\[([^\\[\\]]*)]");

    private static final Pattern FIELDS_KEY = Pattern.compile("fields\\[([^\\[\\]]+)]");

    public JsonApiQuery {
        sorts = List.copyOf(sorts);
        fields = Map.copyOf(fields);
        includes = List.copyOf(includes);
    }

    /** One sort: the path ordered by and whether it ascends. */
    public record SortField(String path, boolean ascending) {
    }

    /** The fields of a resource type to send, or null to send them all. */
    public Set<String> fieldsOf(String resourceType) {
        return fields.get(resourceType);
    }

    public static JsonApiQuery parse(Map<String, String[]> parameters) {
        Map<String, FilterSpec> filters = new LinkedHashMap<>();
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> parameter : parameters.entrySet()) {
            Matcher filter = FILTER_KEY.matcher(parameter.getKey());
            Matcher sparse = FIELDS_KEY.matcher(parameter.getKey());
            if (filter.matches()) {
                readFilter(filters, segments(filter.group(1)), parameter.getValue(), parameter.getKey());
            } else if (sparse.matches()) {
                fields.put(sparse.group(1), new LinkedHashSet<>(list(parameter.getValue()[0])));
            }
        }
        return new JsonApiQuery(filterTree(filters), sorts(first(parameters, "sort")),
                number(first(parameters, "page[offset]"), 0, "page[offset]"),
                Math.min(number(first(parameters, "page[limit]"), MAX_LIMIT, "page[limit]"), MAX_LIMIT),
                fields, includes(first(parameters, "include")));
    }

    private static String first(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    private static List<String> segments(String brackets) {
        List<String> segments = new ArrayList<>();
        Matcher segment = SEGMENT.matcher(brackets);
        while (segment.find()) {
            segments.add(segment.group(1));
        }
        return segments;
    }

    private static List<String> list(String commaSeparated) {
        return Arrays.stream(commaSeparated.split(",")).map(String::strip).filter(each -> !each.isEmpty()).toList();
    }

    private static JsonApiException badRequest(String detail) {
        return new JsonApiException(HttpStatus.BAD_REQUEST, detail);
    }

    /** One named filter as its parameters give it, before the tree is put together. */
    private static final class FilterSpec {

        private boolean group;
        private String path;
        private String operator = JsonApiFilter.EQUAL;
        private final List<String> values = new ArrayList<>();
        private boolean listed;
        private String conjunction;
        private String memberOf;
    }

    private static void readFilter(Map<String, FilterSpec> filters, List<String> segments, String[] values,
            String key) {
        String name = segments.getFirst();
        FilterSpec spec = filters.computeIfAbsent(name, ignored -> new FilterSpec());
        String kind = segments.size() > 1 ? segments.get(1) : "value";
        if (kind.equals("value") || kind.equals("operator")) {
            spec.path = name;
            setCondition(spec, kind, segments.subList(Math.min(2, segments.size()), segments.size()), values, key);
        } else if (segments.size() >= 3 && kind.equals("condition")) {
            setCondition(spec, segments.get(2), segments.subList(3, segments.size()), values, key);
        } else if (segments.size() == 3 && kind.equals("group")) {
            spec.group = true;
            switch (segments.get(2)) {
                case "conjunction" -> spec.conjunction = values[0].toUpperCase(Locale.ROOT);
                case "memberOf" -> spec.memberOf = values[0];
                default -> throw badRequest("The filter parameter " + key + " is not understood.");
            }
        } else {
            throw badRequest("The filter parameter " + key + " is not understood.");
        }
    }

    private static void setCondition(FilterSpec spec, String member, List<String> rest, String[] values,
            String key) {
        if (!rest.isEmpty() && !member.equals("value")) {
            throw badRequest("The filter parameter " + key + " is not understood.");
        }
        switch (member) {
            case "path" -> spec.path = values[0];
            case "operator" -> spec.operator = values[0].toUpperCase(Locale.ROOT);
            case "memberOf" -> spec.memberOf = values[0];
            case "value" -> {
                spec.listed |= !rest.isEmpty();
                spec.values.addAll(List.of(values));
            }
            default -> throw badRequest("The filter parameter " + key + " is not understood.");
        }
    }

    private static JsonApiFilter filterTree(Map<String, FilterSpec> filters) {
        if (filters.isEmpty()) {
            return null;
        }
        filters.forEach((name, spec) -> {
            if (spec.memberOf != null && !(filters.containsKey(spec.memberOf) && filters.get(spec.memberOf).group)) {
                throw badRequest("The filter " + name + " is a member of " + spec.memberOf + ", which is not a group.");
            }
        });
        Set<String> placed = new LinkedHashSet<>();
        JsonApiFilter root = new JsonApiFilter.Group(JsonApiFilter.AND, members(filters, null, placed));
        if (placed.size() != filters.size()) {
            throw badRequest("The filter groups " + String.join(", ", filters.keySet().stream()
                    .filter(name -> !placed.contains(name)).toList()) + " are members of each other.");
        }
        return root;
    }

    private static List<JsonApiFilter> members(Map<String, FilterSpec> filters, String group, Set<String> placed) {
        List<JsonApiFilter> members = new ArrayList<>();
        filters.forEach((name, spec) -> {
            if (Objects.equals(spec.memberOf, group)) {
                placed.add(name);
                members.add(spec.group ? groupOf(name, spec, filters, placed) : matchOf(name, spec));
            }
        });
        return members;
    }

    private static JsonApiFilter groupOf(String name, FilterSpec spec, Map<String, FilterSpec> filters,
            Set<String> placed) {
        if (!JsonApiFilter.AND.equals(spec.conjunction) && !JsonApiFilter.OR.equals(spec.conjunction)) {
            throw badRequest("The filter group " + name + " needs a conjunction of AND or OR.");
        }
        return new JsonApiFilter.Group(spec.conjunction, members(filters, name, placed));
    }

    private static JsonApiFilter matchOf(String name, FilterSpec spec) {
        if (spec.path == null || spec.path.isBlank()) {
            throw badRequest("The filter " + name + " has no path.");
        }
        if (!JsonApiFilter.OPERATORS.contains(spec.operator)) {
            throw badRequest("The filter operator " + spec.operator + " is not supported. Use one of "
                    + String.join(", ", JsonApiFilter.OPERATORS) + ".");
        }
        if (spec.values.isEmpty()) {
            throw badRequest("The filter " + name + " has no value.");
        }
        if (spec.operator.equals(JsonApiFilter.IN)) {
            return new JsonApiFilter.Match(spec.path, spec.operator, List.copyOf(spec.values));
        }
        if (spec.listed || spec.values.size() > 1) {
            throw badRequest("The filter " + name + " takes one value with the " + spec.operator + " operator.");
        }
        return new JsonApiFilter.Match(spec.path, spec.operator, spec.values.getFirst());
    }

    private static List<SortField> sorts(String sort) {
        if (sort == null) {
            return List.of();
        }
        List<SortField> sorts = new ArrayList<>();
        for (String each : sort.split(",", -1)) {
            String path = each.strip();
            boolean descending = path.startsWith("-");
            if (descending) {
                path = path.substring(1);
            }
            if (path.isEmpty()) {
                throw badRequest("The sort parameter " + sort + " names an empty path.");
            }
            sorts.add(new SortField(path, !descending));
        }
        return sorts;
    }

    private static int number(String value, int defaultValue, String name) {
        if (value == null) {
            return defaultValue;
        }
        if (!value.matches("\\d{1,9}")) {
            throw badRequest("The " + name + " parameter is not a whole number of zero or more.");
        }
        int number = Integer.parseInt(value);
        if (name.equals("page[limit]") && number == 0) {
            throw badRequest("The page[limit] parameter is at least 1.");
        }
        return number;
    }

    private static List<List<String>> includes(String include) {
        if (include == null) {
            return List.of();
        }
        List<List<String>> includes = new ArrayList<>();
        for (String path : include.split(",", -1)) {
            List<String> segments = List.of(path.strip().split("\\.", -1));
            if (segments.stream().anyMatch(String::isBlank)) {
                throw badRequest("The include parameter " + include + " names an empty path.");
            }
            includes.add(segments);
        }
        return includes;
    }
}
