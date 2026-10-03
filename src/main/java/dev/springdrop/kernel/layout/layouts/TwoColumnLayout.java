package dev.springdrop.kernel.layout.layouts;

import dev.springdrop.kernel.layout.LayoutPlugin;
import dev.springdrop.kernel.layout.LayoutRegion;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.List;

@SpringDropPlugin(id = TwoColumnLayout.ID, type = LayoutPlugin.class)
public class TwoColumnLayout implements LayoutPlugin {

    public static final String ID = "layout_twocol_section";

    public static final String FIRST = "first";

    public static final String SECOND = "second";

    @Override
    public String label() {
        return "Two column";
    }

    @Override
    public List<LayoutRegion> regions() {
        return List.of(new LayoutRegion(FIRST, "First"), new LayoutRegion(SECOND, "Second"));
    }

    @Override
    public List<String> columnWidths() {
        return List.of("50-50", "33-67", "67-33", "25-75", "75-25");
    }
}
