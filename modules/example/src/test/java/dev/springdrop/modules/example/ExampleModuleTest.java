package dev.springdrop.modules.example;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.event.EntityEvent;
import org.junit.jupiter.api.Test;

class ExampleModuleTest {

    @Test
    void pluginDescribesItself() {
        assertThat(new ExamplePlugin().describe()).isEqualTo("example module plugin");
    }

    @Test
    void listenerRecordsEntityEvents() {
        ExampleEntityListener listener = new ExampleEntityListener();

        listener.onEntity(new EntityEvent("a-node", "node", EntityEvent.Phase.INSERT));

        assertThat(listener.observed()).containsExactly("node:INSERT");
    }
}
