package dev.springdrop.kernel.layout.layouts;

import dev.springdrop.kernel.layout.LayoutPlugin;
import dev.springdrop.kernel.layout.LayoutRegion;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.List;

@SpringDropPlugin(id = OneColumnLayout.ID, type = LayoutPlugin.class)
public class OneColumnLayout implements LayoutPlugin {

    public static final String ID = "layout_onecol";

    public static final String CONTENT = "content";

    @Override
    public String label() {
        return "One column";
    }

    @Override
    public List<LayoutRegion> regions() {
        return List.of(new LayoutRegion(CONTENT, "Content"));
    }
}
