package dev.springdrop.modules.example;

import dev.springdrop.kernel.event.EntityEvent;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * An event listener contributed by the example module, reacting to the kernel's
 * entity lifecycle events.
 */
@Component
public class ExampleEntityListener {

    private final List<String> observed = new ArrayList<>();

    @EventListener
    public void onEntity(EntityEvent event) {
        observed.add(event.entityType() + ":" + event.phase());
    }

    public List<String> observed() {
        return List.copyOf(observed);
    }
}
