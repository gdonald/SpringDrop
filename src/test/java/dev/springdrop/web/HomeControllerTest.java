package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersHomePageWithBootstrapAndEditButton() throws Exception {
        String html = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Document document = Jsoup.parse(html);

        assertThat(document.selectFirst("nav.navbar"))
                .as("home page uses the Bootstrap navbar")
                .isNotNull();
        BootstrapAssertions.assertNoOutlineButtons(document);
        BootstrapAssertions.assertEditControlsAreButtons(document);
    }
}
