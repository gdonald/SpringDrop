package dev.springdrop.kernel.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AssetsTest {

    private static final String BUNDLE = "/assets/springdrop.0123456789abcdef.js";

    @Test
    void withoutAggregationEachModuleIsLoadedFromItsOwnAddress() {
        assertThat(new Assets(new AssetProperties(false, BUNDLE)).url("/js/editor.js")).isEqualTo("/js/editor.js");
    }

    @Test
    void withAggregationEveryModuleIsLoadedFromTheBundle() {
        Assets assets = new Assets(new AssetProperties(true, BUNDLE));

        assertThat(assets.url("/js/editor.js")).isEqualTo(BUNDLE).isEqualTo(assets.url("/js/form-states.js"));
    }

    @Test
    void aggregationWithoutABundleIsRefused() {
        assertThatThrownBy(() -> new Assets(new AssetProperties(true, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
