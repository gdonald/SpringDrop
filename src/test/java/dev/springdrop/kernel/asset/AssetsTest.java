package dev.springdrop.kernel.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.cache.Performance;
import dev.springdrop.kernel.cache.PerformanceSettings;
import org.junit.jupiter.api.Test;

class AssetsTest {

    private static final String BUNDLE = "/assets/springdrop.0123456789abcdef.js";

    private static Performance aggregating(boolean aggregate) {
        Performance performance = mock(Performance.class);
        when(performance.settings()).thenReturn(new PerformanceSettings(600, aggregate));
        return performance;
    }

    @Test
    void withoutAggregationEachModuleIsLoadedFromItsOwnAddress() {
        assertThat(new Assets(new AssetProperties(false, BUNDLE), aggregating(false)).url("/js/editor.js"))
                .isEqualTo("/js/editor.js");
    }

    @Test
    void withAggregationEveryModuleIsLoadedFromTheBundle() {
        Assets assets = new Assets(new AssetProperties(true, BUNDLE), aggregating(true));

        assertThat(assets.url("/js/editor.js")).isEqualTo(BUNDLE).isEqualTo(assets.url("/js/form-states.js"));
        assertThat(assets.bundleBuilt()).isTrue();
    }

    @Test
    void aggregationTurnedOnWithoutABundleBuiltLoadsEachModule() {
        Assets assets = new Assets(new AssetProperties(false, null), aggregating(true));

        assertThat(assets.url("/js/editor.js")).isEqualTo("/js/editor.js");
        assertThat(assets.bundleBuilt()).isFalse();
    }

    @Test
    void aggregationWithoutABundleIsRefused() {
        assertThatThrownBy(() -> new Assets(new AssetProperties(true, null), aggregating(true)))
                .isInstanceOf(IllegalStateException.class);
    }
}
