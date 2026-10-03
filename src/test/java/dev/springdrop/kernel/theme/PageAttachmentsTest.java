package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.render.Attachments;
import org.junit.jupiter.api.Test;

class PageAttachmentsTest {

    private static final String PAGE = "<html><head><title>Trips</title></head><body><main></main></body></html>";

    @Test
    void styleSheetsAndHeadTagsEndTheHeadAndScriptsEndTheBody() {
        Attachments attachments = Attachments.NONE
                .withStyleSheet("/css/trips.css")
                .withHeadTag("<link rel=\"alternate\" href=\"/feed\">")
                .withScript("/js/trips.js");

        String html = PageRenderer.withAttachments(PAGE, attachments);

        assertThat(html).contains("<link rel=\"stylesheet\" href=\"/css/trips.css\"><link rel=\"alternate\" "
                + "href=\"/feed\"></head>");
        assertThat(html).contains("<script type=\"module\" src=\"/js/trips.js\"></script></body>");
    }

    @Test
    void aPageAskingForNothingIsLeftAsItWas() {
        assertThat(PageRenderer.withAttachments(PAGE, Attachments.NONE)).isEqualTo(PAGE);
    }

    @Test
    void anAddressIsEscapedBeforeItIsWritten() {
        assertThat(PageRenderer.withAttachments(PAGE, Attachments.NONE.withStyleSheet("/css/a\"b.css")))
                .contains("href=\"/css/a&quot;b.css\"");
    }
}
