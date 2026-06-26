package dev.springdrop.kernel.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@SpringBootTest
class ErrorPageRenderingTest extends AbstractIntegrationTest {

    @Autowired
    private ITemplateEngine templateEngine;

    @Test
    void themedErrorPagesUseBootstrapButtonsWithoutOutlineStyle() {
        for (String template : new String[] {"error/404", "error/403", "error/500"}) {
            Document document = Jsoup.parse(templateEngine.process(template, new Context()));

            assertThat(document.selectFirst("a.btn"))
                    .as("%s has a Bootstrap button back to home", template)
                    .isNotNull();
            BootstrapAssertions.assertNoOutlineButtons(document);
        }
    }
}
