package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.asset.Assets;
import dev.springdrop.kernel.cache.Performance;
import dev.springdrop.kernel.cache.PerformanceSettings;
import dev.springdrop.kernel.cron.AutomatedCron;
import dev.springdrop.kernel.cron.AutomatedCronSettings;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.theme.Messenger;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.ui.ExtendedModelMap;

/** The performance form on a site whose build made no script bundle. */
class PerformanceControllerTest {

    private final AutomatedCron automatedCron = mock(AutomatedCron.class);

    private final Performance performance = mock(Performance.class);

    private final Assets assets = mock(Assets.class);

    private final FormRenderer renderer = new FormRenderer();

    private final PerformanceController controller = new PerformanceController(automatedCron, performance, assets,
            new FormBuilder(List.of(), renderer, mock(ApplicationEventPublisher.class)), renderer, mock(Messenger.class));

    @BeforeEach
    void noBundle() {
        when(assets.bundleBuilt()).thenReturn(false);
        when(automatedCron.settings()).thenReturn(AutomatedCronSettings.DEFAULTS);
        when(performance.settings()).thenReturn(new PerformanceSettings(600, false));
    }

    @Test
    void theAggregationBoxIsDisabledAndSaysWhy() {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.form(model);

        var box = Jsoup.parse((String) model.get("formMarkup")).selectFirst("input[name=preprocess_js]");
        assertThat(box.hasAttr("disabled")).isTrue();
        assertThat((String) model.get("formMarkup")).contains(PerformanceController.NO_BUNDLE);
    }

    @Test
    void turningAggregationOnIsRefused() {
        ExtendedModelMap model = new ExtendedModelMap();

        controller.save(Map.of(PerformanceController.AUTOMATED_CRON_INTERVAL, "0",
                PerformanceController.PAGE_CACHE_MAX_AGE, "600", PerformanceController.AGGREGATE_JS, "true"), model);

        assertThat((String) model.get("formMarkup")).contains(PerformanceController.NO_BUNDLE);
        verify(performance, never()).save(any());
    }
}
