package dev.springdrop.kernel.jsonapi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.EntityData;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonApiNormalizerTest {

    @Test
    void anEntityWithoutAUuidIsIdentifiedByItsId() {
        assertThat(JsonApiNormalizer.idOf(EntityData.of("node", 4L, "article", "Spring schedule", Map.of())))
                .isEqualTo("4");
    }
}
