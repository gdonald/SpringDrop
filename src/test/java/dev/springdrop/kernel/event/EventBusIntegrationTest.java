package dev.springdrop.kernel.event;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

@SpringBootTest
class EventBusIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Autowired
    private Recorder recorder;

    @BeforeEach
    void reset() {
        recorder.calls.clear();
    }

    @Test
    void twoListenersReactToTheSameEventInOrder() {
        publisher.publishEvent(new EntityEvent("a-node", "node", EntityEvent.Phase.INSERT));

        assertThat(recorder.calls).containsExactly("first:INSERT:a-node", "second:node");
    }

    @Test
    void alterListenersMutateTheSubjectObservedByTheCaller() {
        TagsAlterEvent event = new TagsAlterEvent(new ArrayList<>(List.of("base")));

        publisher.publishEvent(event);

        assertThat(event.subject()).containsExactly("base", "from-a", "from-b");
    }

    static class Recorder {
        final List<String> calls = new ArrayList<>();
    }

    static class TagsAlterEvent extends AlterEvent<List<String>> {
        TagsAlterEvent(List<String> subject) {
            super(subject);
        }
    }

    @TestConfiguration
    static class Listeners {

        @Bean
        Recorder recorder() {
            return new Recorder();
        }

        @Bean
        EntityListeners entityListeners(Recorder recorder) {
            return new EntityListeners(recorder);
        }

        @Bean
        TagListeners tagListeners() {
            return new TagListeners();
        }

        static class EntityListeners {
            private final Recorder recorder;

            EntityListeners(Recorder recorder) {
                this.recorder = recorder;
            }

            @EventListener
            @Order(1)
            void first(EntityEvent event) {
                recorder.calls.add("first:" + event.phase() + ":" + event.entity());
            }

            @EventListener
            @Order(2)
            void second(EntityEvent event) {
                recorder.calls.add("second:" + event.entityType());
            }
        }

        static class TagListeners {
            @EventListener
            @Order(1)
            void addA(TagsAlterEvent event) {
                event.subject().add("from-a");
            }

            @EventListener
            @Order(2)
            void addB(TagsAlterEvent event) {
                event.subject().add("from-b");
            }
        }
    }
}
