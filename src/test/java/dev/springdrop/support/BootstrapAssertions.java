package dev.springdrop.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Reusable assertions enforcing the project's Bootstrap control conventions on
 * rendered markup: no outline-style buttons, and Edit controls rendered as
 * buttons.
 */
public final class BootstrapAssertions {

    private BootstrapAssertions() {
    }

    public static void assertNoOutlineButtons(Document document) {
        assertThat(document.select("[class*=btn-outline]"))
                .as("page must not use outline-style buttons")
                .isEmpty();
    }

    public static void assertEditControlsAreButtons(Document document) {
        List<Element> editControls = document.select("a, button").stream()
                .filter(element -> element.text().trim().equalsIgnoreCase("Edit"))
                .toList();

        assertThat(editControls).as("page has at least one Edit control").isNotEmpty();
        for (Element control : editControls) {
            assertThat(control.hasClass("btn"))
                    .as("Edit control '%s' is styled as a button", control.outerHtml())
                    .isTrue();
            assertThat(control.className())
                    .as("Edit control is not an outline-style button")
                    .doesNotContain("btn-outline");
        }
    }
}
