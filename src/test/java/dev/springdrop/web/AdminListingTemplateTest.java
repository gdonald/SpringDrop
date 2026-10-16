package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every admin overview template uses the shared listing pattern: a table with
 * the admin-listing class, the local tasks tab bar, and row and local actions
 * drawn by the shared fragments, so Edit is always a button and no button is
 * outline-style. An admin template with a table that is not an overview is
 * named here, so a new overview cannot leave the pattern out unnoticed.
 */
class AdminListingTemplateTest {

    private static final Path ADMIN_TEMPLATES = Path.of("src/main/resources/templates/admin");

    /** Admin pages with a table that list nothing to act on row by row: forms, reports, and editors. */
    private static final Set<String> NOT_OVERVIEWS = Set.of("activity-log-event.html", "config-translation-item.html",
            "layout-builder.html", "workflow.html", "rest-resources.html", "status-report.html",
            "token-browser.html");

    private static final Pattern TABLE = Pattern.compile("<table\\b[^>]*>");

    private static final Pattern INLINE_EDIT = Pattern.compile(">\\s*Edit\\s*</a>");

    private static final Pattern INLINE_ROW_BUTTON = Pattern.compile("<a\\b[^>]*class=\"[^\"]*\\bbtn-sm\\b");

    private static List<Path> overviews() throws IOException {
        try (Stream<Path> templates = Files.list(ADMIN_TEMPLATES)) {
            return templates.filter(template -> !NOT_OVERVIEWS.contains(template.getFileName().toString()))
                    .filter(template -> read(template).contains("<table")).sorted().toList();
        }
    }

    private static String read(Path template) {
        try {
            return Files.readString(template);
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }

    @Test
    void everyAdminOverviewUsesTheSharedListingPattern() throws IOException {
        List<Path> overviews = overviews();

        assertThat(overviews).hasSizeGreaterThanOrEqualTo(29);
        assertThat(overviews).allSatisfy(template -> {
            String source = read(template);
            assertThat(TABLE.matcher(source).results().map(table -> table.group()))
                    .as("%s draws its tables with the admin-listing class", template)
                    .allSatisfy(table -> assertThat(table).contains("admin-listing"));
            assertThat(source).as("%s draws the local tasks", template)
                    .contains("~{fragments/actions :: localTasks(${localTasks})}");
            assertThat(INLINE_EDIT.matcher(source).find()).as("%s draws Edit through the shared fragment", template)
                    .isFalse();
            assertThat(INLINE_ROW_BUTTON.matcher(source).find())
                    .as("%s draws its row actions through the shared fragments", template).isFalse();
        });
    }

    @Test
    void noAdminTemplateHasAnOutlineButton() throws IOException {
        try (Stream<Path> templates = Files.list(ADMIN_TEMPLATES)) {
            assertThat(templates.toList()).allSatisfy(template -> assertThat(read(template))
                    .as("%s has no outline-style button", template).doesNotContain("btn-outline"));
        }
    }

    @Test
    void theSharedFragmentsDrawButtons() {
        String fragments = read(Path.of("src/main/resources/templates/fragments/actions.html"));

        assertThat(fragments).contains("th:fragment=\"edit(href)\"", "class=\"btn btn-secondary btn-sm\" "
                + "data-row-action=\"edit\"", "th:fragment=\"localAction(label, href)\"", "class=\"btn btn-primary\"",
                "th:fragment=\"localTasks(tabs)\"", "th:fragment=\"empty(message)\"").doesNotContain("btn-outline");
    }
}
