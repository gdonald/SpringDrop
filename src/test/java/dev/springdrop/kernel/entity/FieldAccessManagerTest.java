package dev.springdrop.kernel.entity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.access.AccessResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldAccessManagerTest {

    private static final EntityType NOTE = EntityType.content("note", EntityData.class);

    private static final EntityData ENTITY = EntityData.of("note", 1L, null, "Opening hours", Map.of());

    private static FieldAccessRule saying(AccessResult result) {
        return (type, fieldName, operation, entity, authentication) -> result;
    }

    @Test
    void aFieldNoRuleForbidsIsAllowed() {
        FieldAccessManager manager = new FieldAccessManager(List.of(saying(AccessResult.neutral())));

        assertThat(manager.may(NOTE, "summary", FieldAccessRule.VIEW, ENTITY, null)).isTrue();
    }

    @Test
    void oneRuleForbiddingSettlesItWhateverTheOthersSay() {
        FieldAccessManager manager = new FieldAccessManager(List.of(saying(AccessResult.allow()),
                saying(AccessResult.forbid("Closed.")), saying(AccessResult.allow())));

        AccessResult result = manager.check(NOTE, "summary", FieldAccessRule.EDIT, ENTITY, null);

        assertThat(result.forbidden()).isTrue();
        assertThat(result.reason()).isEqualTo("Closed.");
    }
}
