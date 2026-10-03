package dev.springdrop.kernel.filter;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.filter.filters.HtmlCorrectorFilter;
import dev.springdrop.kernel.filter.filters.HtmlEscapeFilter;
import dev.springdrop.kernel.filter.filters.HtmlRestrictorFilter;
import dev.springdrop.kernel.filter.filters.LineBreakFilter;
import dev.springdrop.kernel.filter.filters.MarkdownFilter;
import dev.springdrop.kernel.filter.filters.UrlFilter;
import dev.springdrop.kernel.form.FormElement;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TextFilterTest {

    private final HtmlSanitizer sanitizer = new HtmlSanitizer();

    @Nested
    class AllowedTags {

        private final HtmlRestrictorFilter filter = new HtmlRestrictorFilter(sanitizer);

        private String restricted(String text, String allowed) {
            return filter.process(text, Map.of(HtmlRestrictorFilter.ALLOWED_HTML, allowed));
        }

        @Test
        void tagsTheSettingNamesAreKeptAndTheRestRemovedWithTheirTextLeft() {
            assertThat(restricted("<p>Open <em>daily</em> <u>now</u></p>", "<p> <em>"))
                    .isEqualTo("<p>Open <em>daily</em> now</p>");
        }

        @Test
        void attributesAreKeptOnlyWhereTheSettingNamesThem() {
            assertThat(restricted("<a href=\"https://example.com\" title=\"x\">Map</a>", "<a href>"))
                    .isEqualTo("<a href=\"https://example.com\">Map</a>");
        }

        @Test
        void scriptsAndEventHandlersAreNeverKeptEvenWhenTheSettingNamesThem() {
            assertThat(restricted("<p onclick=\"steal()\">Hi</p><script>steal()</script>", "<p onclick> <script>"))
                    .isEqualTo("<p>Hi</p>");
        }

        @Test
        void aLinkToScriptIsDropped() {
            assertThat(restricted("<a href=\"javascript:steal()\">Hi</a>", "<a href>")).isEqualTo("Hi");
        }

        @Test
        void theSettingIsEditedAsAListOfTags() {
            FormElement element = filter.settingsForm("filter_html_", filter.defaultSettings()).getFirst();

            assertThat(element.name()).isEqualTo("filter_html_" + HtmlRestrictorFilter.ALLOWED_HTML);
            assertThat(String.valueOf(element.value())).startsWith("<a href hreflang>");
            assertThat(filter.settingsValues("filter_html_", Map.of("filter_html_allowed_html", "<p>")))
                    .containsEntry(HtmlRestrictorFilter.ALLOWED_HTML, "<p>");
            assertThat(filter.label()).isNotBlank();
        }

        @Test
        void theTagsASettingNamesAreReadInOrderWithEachTagsAttributesTogether() {
            AllowedHtml allowed = AllowedHtml.parse("<P class> <a href> <script> <a hreflang onclick style> <br>");

            assertThat(allowed.tagNames()).containsExactly("p", "a", "br");
            assertThat(allowed.tags().get("a")).containsExactly("href", "hreflang");
        }
    }

    @Nested
    class LineBreaks {

        private final LineBreakFilter filter = new LineBreakFilter();

        @Test
        void blankLinesSeparateParagraphsAndSingleBreaksBecomeLineBreaks() {
            assertThat(filter.process("Open daily.\nClosed Sunday.\r\n\r\nSee you.", Map.of()))
                    .isEqualTo("<p>Open daily.<br>\nClosed Sunday.</p>\n<p>See you.</p>");
        }

        @Test
        void aRunThatStartsWithABlockIsLeftAlone() {
            assertThat(filter.process("<ul><li>One</li></ul>\n\n\n\nAfter", Map.of()))
                    .isEqualTo("<ul><li>One</li></ul>\n<p>After</p>");
            assertThat(filter.label()).isNotBlank();
        }

        @Test
        void blankLinesAtTheStartMakeNoEmptyParagraph() {
            assertThat(filter.process("\n\nAfter", Map.of())).isEqualTo("<p>After</p>");
        }
    }

    @Nested
    class Addresses {

        private final UrlFilter filter = new UrlFilter();

        private String linked(String text) {
            return filter.process(text, filter.defaultSettings());
        }

        @Test
        void webAndMailAddressesBecomeLinks() {
            assertThat(linked("See https://example.com/hours, www.example.org or mail desk@example.com."))
                    .isEqualTo("See <a href=\"https://example.com/hours\">https://example.com/hours</a>, "
                            + "<a href=\"http://www.example.org\">www.example.org</a> or mail "
                            + "<a href=\"mailto:desk@example.com\">desk@example.com</a>.");
        }

        @Test
        void addressesAlreadyInsideALinkOrInNestedTagsAreHandled() {
            assertThat(linked("<a href=\"https://example.com\">https://example.com</a> <em>www.example.org</em>"))
                    .isEqualTo("<a href=\"https://example.com\">https://example.com</a> "
                            + "<em><a href=\"http://www.example.org\">www.example.org</a></em>");
        }

        @Test
        void aLongAddressIsShownCutWhileTheLinkKeepsAllOfIt() {
            assertThat(filter.process("https://example.com/a/long/path", Map.of(UrlFilter.LENGTH, 10)))
                    .isEqualTo("<a href=\"https://example.com/a/long/path\">https://ex...</a>");
        }

        @Test
        void textWithNoAddressIsLeftAsItWas() {
            assertThat(linked("Open daily &amp; late")).isEqualTo("Open daily &amp; late");
        }

        @Test
        void theLengthIsEditedAsAWholeNumber() {
            assertThat(filter.settingsForm("filter_url_", Map.of()).getFirst().value())
                    .isEqualTo(UrlFilter.DEFAULT_LENGTH);
            assertThat(filter.settingsValues("filter_url_", Map.of("filter_url_length", "40")))
                    .containsEntry(UrlFilter.LENGTH, 40);
            assertThat(filter.settingsValues("filter_url_", Map.of("filter_url_length", "long")))
                    .containsEntry(UrlFilter.LENGTH, UrlFilter.DEFAULT_LENGTH);
            assertThat(filter.label()).isNotBlank();
        }
    }

    @Test
    void faultyHtmlIsCorrected() {
        HtmlCorrectorFilter filter = new HtmlCorrectorFilter();

        assertThat(filter.process("<p>Open <em>daily</p></div>", Map.of())).isEqualTo("<p>Open <em>daily</em></p>");
        assertThat(filter.label()).isNotBlank();
    }

    @Test
    void plainTextShowsTagsAsWritten() {
        HtmlEscapeFilter filter = new HtmlEscapeFilter();

        assertThat(filter.process("<b>Bold</b> & more", Map.of())).isEqualTo("&lt;b&gt;Bold&lt;/b&gt; &amp; more");
        assertThat(filter.label()).isNotBlank();
        assertThat(filter.defaultSettings()).isEmpty();
        assertThat(filter.settingsForm("x", Map.of())).isEmpty();
        assertThat(filter.settingsValues("x", Map.of())).isEmpty();
    }

    @Test
    void markdownIsRenderedAndScriptLinksDropped() {
        MarkdownFilter filter = new MarkdownFilter();

        assertThat(filter.process("## Hours\n\nOpen *daily*. [Map](javascript:steal())", Map.of()))
                .isEqualTo("<h2>Hours</h2>\n<p>Open <em>daily</em>. <a rel=\"nofollow\" href=\"\">Map</a></p>");
        assertThat(filter.label()).isNotBlank();
    }
}
