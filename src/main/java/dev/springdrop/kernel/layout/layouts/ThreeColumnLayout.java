package dev.springdrop.kernel.layout.layouts;

import dev.springdrop.kernel.layout.LayoutPlugin;
import dev.springdrop.kernel.layout.LayoutRegion;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.List;

@SpringDropPlugin(id = ThreeColumnLayout.ID, type = LayoutPlugin.class)
public class ThreeColumnLayout implements LayoutPlugin {

    public static final String ID = "layout_threecol_section";

    public static final String FIRST = "first";

    public static final String SECOND = "second";

    public static final String THIRD = "third";

    @Override
    public String label() {
        return "Three column";
    }

    @Override
    public List<LayoutRegion> regions() {
        return List.of(
                new LayoutRegion(FIRST, "First"),
                new LayoutRegion(SECOND, "Second"),
                new LayoutRegion(THIRD, "Third"));
    }

    @Override
    public List<String> columnWidths() {
        return List.of("33-34-33", "25-50-25", "25-25-50", "50-25-25");
    }
}
