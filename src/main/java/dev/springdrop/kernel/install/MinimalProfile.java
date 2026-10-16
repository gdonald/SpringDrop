package dev.springdrop.kernel.install;

import java.util.List;
import org.springframework.stereotype.Component;

/** Core alone: no modules beyond those core needs, and no content types, fields, or other starting config. */
@Component
public class MinimalProfile implements InstallProfile {

    public static final String ID = "minimal";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Minimal";
    }

    @Override
    public String description() {
        return "Start with core alone, for a site built from the ground up.";
    }

    @Override
    public int weight() {
        return 10;
    }

    @Override
    public List<String> modules() {
        return List.of();
    }
}
