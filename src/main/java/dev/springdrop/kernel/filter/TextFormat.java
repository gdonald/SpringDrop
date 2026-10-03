package dev.springdrop.kernel.filter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * A text format: the pipeline of filters text written in it runs through, in
 * weight order, where the format sorts, and whether it is the fallback format
 * every person may use. Who may use any other format is a permission per format,
 * which roles are granted. Stored as the config entity {@code filter.format.<id>}.
 */
public record TextFormat(String id, String label, int weight, boolean fallback, List<FilterConfig> filters) {

    public static final String CONFIG_PREFIX = "filter.format";

    public TextFormat {
        filters = filters.stream().sorted(Comparator.comparingInt(FilterConfig::weight)).toList();
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public Optional<FilterConfig> filter(String filterId) {
        return filters.stream().filter(filter -> filter.id().equals(filterId)).findFirst();
    }

    public TextFormat withLabel(String newLabel) {
        return new TextFormat(id, newLabel, weight, fallback, filters);
    }

    public TextFormat withWeight(int newWeight) {
        return new TextFormat(id, label, newWeight, fallback, filters);
    }

    public TextFormat withFilters(List<FilterConfig> newFilters) {
        return new TextFormat(id, label, weight, fallback, new ArrayList<>(newFilters));
    }
}
