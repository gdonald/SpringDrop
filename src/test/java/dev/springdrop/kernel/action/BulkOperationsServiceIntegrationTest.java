package dev.springdrop.kernel.action;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.action.plugins.PublishNode;
import dev.springdrop.kernel.action.plugins.UnpublishNode;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewOptions;
import dev.springdrop.kernel.views.handlers.BulkFormField;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BulkOperationsServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BulkOperations bulk;

    private static ViewConfig listing(List<HandlerConfig> fields) {
        ViewOptions options = new ViewOptions(fields, null, null, null, null, null, null, null, null);
        return new ViewConfig("listing", "Listing", "", NodeEntityType.ID, List.of(
                new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "Listing", options, Map.of())));
    }

    @Test
    void aBulkFieldNamingActionsOffersOnlyThose() {
        ViewConfig view = listing(List.of(HandlerConfig.of("bulk", BulkFormField.ID, "id",
                Map.of(BulkFormField.ACTIONS, " node_unpublish, node_publish,, "))));

        assertThat(bulk.offered(view, ViewDisplay.DEFAULT)).extracting(ActionConfig::id)
                .containsExactly(PublishNode.ID, UnpublishNode.ID);
    }

    @Test
    void aListingWithoutFieldsOffersNothing() {
        assertThat(bulk.offered(listing(null), ViewDisplay.DEFAULT)).isEmpty();
    }

    @Test
    void anItemIsATypeAndANumericId() {
        assertThat(BulkOperations.Item.parse("node:42")).contains(new BulkOperations.Item("node", 42L));
        assertThat(BulkOperations.Item.parse("node:42:1")).isEmpty();
        assertThat(BulkOperations.Item.parse("node:")).isEmpty();
    }
}
