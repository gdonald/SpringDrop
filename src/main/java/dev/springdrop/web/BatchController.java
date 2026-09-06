package dev.springdrop.web;

import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchProgress;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.util.HtmlUtils;

/**
 * Watches a batch run. The page shows a progress bar that asks for the next
 * chunk as soon as it loads and again after each one, so the work advances in
 * steps a request can carry. When the batch finishes, the bar is replaced by a
 * link onward, and a browser without scripting can press the button instead.
 */
@Controller
public class BatchController {

    public static final String PATH = "/batch/{batchId}";

    public static final String RUN_PATH = "/batch/{batchId}/run";

    private final BatchManager batches;

    public BatchController(BatchManager batches) {
        this.batches = batches;
    }

    public static String pathFor(String batchId) {
        return PATH.replace("{batchId}", batchId);
    }

    @GetMapping(PATH)
    public String show(@PathVariable String batchId, Model model) {
        BatchProgress progress = batches.progress(batchId).orElseThrow(
                () -> new IllegalArgumentException("No batch running with id '" + batchId + "'"));

        model.addAttribute("batchId", batchId);
        model.addAttribute("progressMarkup", fragment(batchId, progress));
        return "batch/progress";
    }

    @PostMapping(RUN_PATH)
    public ResponseEntity<String> run(@PathVariable String batchId) {
        BatchProgress progress = batches.processChunk(batchId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(fragment(batchId, progress));
    }

    /** The bar as it stands, asking for the next chunk while there is one. */
    private String fragment(String batchId, BatchProgress progress) {
        String finishedPath = batches.definition(batchId)
                .map(definition -> definition.finishedPath())
                .orElse("/");

        StringBuilder markup = new StringBuilder("<div id=\"batch-progress\"");
        if (!progress.finished()) {
            markup.append(" hx-post=\"").append(HtmlUtils.htmlEscape("/batch/" + batchId + "/run")).append("\"")
                    .append(" hx-trigger=\"load delay:100ms\"")
                    .append(" hx-swap=\"outerHTML\"");
        }
        markup.append(">")
                .append("<div class=\"progress\" role=\"progressbar\"")
                .append(" aria-valuenow=\"").append(progress.percentage()).append("\"")
                .append(" aria-valuemin=\"0\" aria-valuemax=\"100\">")
                .append("<div class=\"progress-bar\" style=\"width: ").append(progress.percentage())
                .append("%\">").append(progress.percentage()).append("%</div></div>")
                .append("<p class=\"mt-2\">")
                .append(progress.processed()).append(" of ").append(progress.total())
                .append(" done</p>");

        for (String error : progress.errors()) {
            markup.append("<p class=\"text-danger mb-1\">").append(HtmlUtils.htmlEscape(error)).append("</p>");
        }
        if (progress.finished()) {
            markup.append("<a class=\"btn btn-primary\" href=\"")
                    .append(HtmlUtils.htmlEscape(finishedPath)).append("\">Continue</a>");
        } else {
            markup.append("<form method=\"post\" action=\"")
                    .append(HtmlUtils.htmlEscape("/batch/" + batchId + "/run")).append("\">")
                    .append("<button type=\"submit\" class=\"btn btn-secondary\">Carry on</button></form>");
        }
        return markup.append("</div>").toString();
    }
}
