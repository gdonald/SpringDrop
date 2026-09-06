package dev.springdrop.kernel.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import dev.springdrop.web.BatchController;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BatchIntegrationTest extends AbstractIntegrationTest {

    static final String COUNTING = "counting";

    static final String FAILING = "failing";

    private static final String BATCH_ID = "test-batch";

    @Autowired
    private BatchManager batches;

    @Autowired
    private CountingHandler counting;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private dev.springdrop.kernel.state.StateService stateService;

    @BeforeEach
    void nothingCountedYet() {
        counting.counted.clear();
        batches.forget(BATCH_ID);
    }

    private BatchDefinition batchOf(int operations) {
        List<BatchOperationSpec> specs = new ArrayList<>();
        for (int index = 0; index < operations; index++) {
            specs.add(BatchOperationSpec.of(COUNTING, Map.of("item", "item-" + index)));
        }
        return BatchDefinition.of(BATCH_ID, specs).inChunksOf(2).finishingAt("/done");
    }

    @Test
    void aBatchStartsWithNothingDoneAndEverythingAhead() {
        BatchProgress progress = batches.start(batchOf(5));

        assertThat(progress.total()).isEqualTo(5);
        assertThat(progress.processed()).isZero();
        assertThat(progress.remaining()).isEqualTo(5);
        assertThat(progress.percentage()).isZero();
        assertThat(progress.finished()).isFalse();
    }

    @Test
    void eachChunkCarriesOutOnlyItsShare() {
        batches.start(batchOf(5));

        BatchProgress first = batches.processChunk(BATCH_ID);
        assertThat(first.processed()).isEqualTo(2);
        assertThat(first.percentage()).isEqualTo(40);
        assertThat(counting.counted).containsExactly("item-0", "item-1");

        BatchProgress second = batches.processChunk(BATCH_ID);
        assertThat(second.processed()).isEqualTo(4);
        assertThat(second.finished()).isFalse();
    }

    @Test
    void theLastChunkFinishesTheBatch() {
        batches.start(batchOf(5));

        BatchProgress progress = batches.processAll(BATCH_ID);

        assertThat(progress.finished()).isTrue();
        assertThat(progress.processed()).isEqualTo(5);
        assertThat(progress.percentage()).isEqualTo(100);
        assertThat(counting.counted).hasSize(5);
    }

    @Test
    void aFinishedBatchRunsNothingMoreHoweverOftenItIsAskedTo() {
        batches.start(batchOf(2));
        batches.processAll(BATCH_ID);

        BatchProgress again = batches.processChunk(BATCH_ID);

        assertThat(again.finished()).isTrue();
        assertThat(counting.counted).hasSize(2);
    }

    @Test
    void anOperationThatFailsIsRecordedAndTheRestCarryOn() {
        batches.start(new BatchDefinition(BATCH_ID, List.of(
                BatchOperationSpec.of(COUNTING, Map.of("item", "first")),
                BatchOperationSpec.of(FAILING, Map.of()),
                BatchOperationSpec.of(COUNTING, Map.of("item", "third"))), 3, "/done"));

        BatchProgress progress = batches.processAll(BATCH_ID);

        assertThat(progress.finished()).isTrue();
        assertThat(progress.errors()).singleElement().asString().contains("This one will not do");
        assertThat(counting.counted).containsExactly("first", "third");
    }

    @Test
    void anOperationNamingAHandlerThatIsNotThereIsRecorded() {
        batches.start(BatchDefinition.of(BATCH_ID,
                List.of(BatchOperationSpec.of("nonesuch", Map.of()))));

        BatchProgress progress = batches.processAll(BATCH_ID);

        assertThat(progress.errors()).singleElement().asString().contains("nonesuch");
    }

    @Test
    void anEmptyBatchIsFinishedAsSoonAsItRuns() {
        batches.start(BatchDefinition.of(BATCH_ID, List.of()));

        BatchProgress progress = batches.processChunk(BATCH_ID);

        assertThat(progress.finished()).isTrue();
        assertThat(progress.percentage()).isEqualTo(100);
    }

    @Test
    void aBatchThatWasNeverStartedCannotBeRun() {
        assertThatThrownBy(() -> batches.processChunk("no-such-batch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-such-batch");
    }

    @Test
    void aBatchWhoseProgressWasLostCannotBeRun() {
        stateService.set(BatchManager.STATE_COLLECTION, BATCH_ID + ".definition", batchOf(2));

        assertThatThrownBy(() -> batches.processChunk(BATCH_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("progress");
    }

    @Test
    void aForgottenBatchLeavesNothingBehind() {
        batches.start(batchOf(2));

        batches.forget(BATCH_ID);

        assertThat(batches.progress(BATCH_ID)).isEmpty();
        assertThat(batches.definition(BATCH_ID)).isEmpty();
    }

    @Test
    void theProgressPageShowsHowFarAlongTheBatchIs() throws Exception {
        batches.start(batchOf(4));
        batches.processChunk(BATCH_ID);

        Document document = Jsoup.parse(mockMvc.perform(get(BatchController.pathFor(BATCH_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.selectFirst(".progress-bar").text()).isEqualTo("50%");
        assertThat(document.selectFirst("#batch-progress").attr("hx-post"))
                .isEqualTo("/batch/" + BATCH_ID + "/run");
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void aBatchThatIsNotRunningHasNoPageToWatch() {
        assertThatThrownBy(() -> mockMvc.perform(get(BatchController.pathFor("no-such-batch"))))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePageOffersAButtonForABrowserThatIsNotRunningTheScript() throws Exception {
        batches.start(batchOf(4));

        Document document = Jsoup.parse(mockMvc.perform(get(BatchController.pathFor(BATCH_ID)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.selectFirst("form[action=/batch/" + BATCH_ID + "/run] button").text())
                .isEqualTo("Carry on");
    }

    @Test
    void askingForTheNextChunkAdvancesTheBarAndReturnsItAlone() throws Exception {
        batches.start(batchOf(4));

        String fragment = mockMvc.perform(post("/batch/" + BATCH_ID + "/run").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment).doesNotContain("<html");
        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst(".progress-bar").text()).isEqualTo("50%");
        assertThat(counting.counted).hasSize(2);
    }

    @Test
    void theFinishedBarStopsAskingAndPointsOnward() throws Exception {
        batches.start(batchOf(2));

        String fragment = mockMvc.perform(post("/batch/" + BATCH_ID + "/run").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document document = Jsoup.parseBodyFragment(fragment);
        assertThat(document.selectFirst("#batch-progress").hasAttr("hx-post")).isFalse();
        assertThat(document.selectFirst("a.btn").attr("href")).isEqualTo("/done");
        assertThat(document.selectFirst("a.btn").text()).isEqualTo("Continue");
    }

    @Test
    void anErrorFromAChunkIsShownOnTheBar() throws Exception {
        batches.start(BatchDefinition.of(BATCH_ID, List.of(BatchOperationSpec.of(FAILING, Map.of()))));

        String fragment = mockMvc.perform(post("/batch/" + BATCH_ID + "/run").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst(".text-danger").text())
                .contains("This one will not do");
    }

    @TestConfiguration
    static class Handlers {

        @Bean
        CountingHandler countingHandler() {
            return new CountingHandler();
        }

        @Bean
        FailingHandler failingHandler() {
            return new FailingHandler();
        }
    }

    /** Records what it was given, so the test can see what ran and in what order. */
    static class CountingHandler implements BatchOperationHandler {

        final List<String> counted = new ArrayList<>();

        @Override
        public String id() {
            return COUNTING;
        }

        @Override
        public void run(Map<String, Object> payload) {
            counted.add(String.valueOf(payload.get("item")));
        }
    }

    /** Stands in for an operation that goes wrong. */
    static class FailingHandler implements BatchOperationHandler {

        @Override
        public String id() {
            return FAILING;
        }

        @Override
        public void run(Map<String, Object> payload) {
            throw new IllegalStateException("This one will not do");
        }
    }
}
