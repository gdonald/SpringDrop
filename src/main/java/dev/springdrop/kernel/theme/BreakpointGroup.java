package dev.springdrop.kernel.theme;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The breakpoints one theme declares, narrowest first. A theme's group has the
 * theme's name. A module contributes a group by declaring a bean of this type.
 */
public record BreakpointGroup(String id, String label, List<Breakpoint> breakpoints) {

    public BreakpointGroup {
        breakpoints = breakpoints.stream().sorted(Comparator.comparingInt(Breakpoint::weight)).toList();
    }

    public Optional<Breakpoint> breakpoint(String breakpointId) {
        return breakpoints.stream().filter(breakpoint -> breakpoint.id().equals(breakpointId)).findFirst();
    }
}
