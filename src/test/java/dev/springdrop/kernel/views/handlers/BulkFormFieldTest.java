package dev.springdrop.kernel.views.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.views.FieldHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ResultRow;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BulkFormFieldTest {

    private final BulkFormField field = new BulkFormField();

    @Test
    void theHeadingIsTheLabelWhenOneIsSet() {
        assertThat(field.heading(HandlerConfig.of("bulk", BulkFormField.ID, "id", Map.of()))).isEqualTo("Select");
        assertThat(field.heading(HandlerConfig.of("bulk", BulkFormField.ID, "id",
                Map.of(FieldHandler.LABEL, "Pick")))).isEqualTo("Pick");
        assertThat(field.sortable()).isFalse();
        assertThat(field.label()).isEqualTo("Bulk operations");
    }

    @Test
    void aRowWhoseRelationshipReachesNothingHasNoBox() {
        EntityData node = EntityData.of("node", 4L, "article", "Spring schedule", Map.of());

        assertThat(field.render(new ResultRow(node, Map.of()),
                HandlerConfig.of("bulk", BulkFormField.ID, "id", Map.of()).through("author"))).isEmpty();
    }

    @Test
    void theSettingsAreTheLabelAndTheActionsOffered() {
        assertThat(field.settingsForm("settings_", Map.of(BulkFormField.ACTIONS, "node_publish")))
                .extracting(FormElement::name).containsExactly("settings_label", "settings_actions");
        assertThat(field.settingsValues("settings_", Map.of("settings_label", "Pick",
                "settings_actions", " node_publish "))).isEqualTo(Map.of(FieldHandler.LABEL, "Pick",
                BulkFormField.ACTIONS, "node_publish"));
    }
}
