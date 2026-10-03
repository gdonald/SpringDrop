package dev.springdrop.kernel.theme;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The breakpoint groups the site's themes and modules declare. */
@Component
public class BreakpointManager {

    private final List<BreakpointGroup> groups;

    public BreakpointManager(List<BreakpointGroup> groups) {
        this.groups = groups.stream().sorted(Comparator.comparing(BreakpointGroup::label)).toList();
    }

    /** Every group, by label. */
    public List<BreakpointGroup> groups() {
        return groups;
    }

    public Optional<BreakpointGroup> group(String id) {
        return groups.stream().filter(group -> group.id().equals(id)).findFirst();
    }
}
