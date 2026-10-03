package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class AssetAggregationIntegrationTest {

    private static final Pattern IMPORT = Pattern.compile("from \"([^\"]+)\"");

    private static String importedFrom(MockMvc mockMvc) throws Exception {
        String html = mockMvc.perform(get("/user/login")).andReturn().getResponse().getContentAsString();
        Matcher found = IMPORT.matcher(html);
        assertThat(found.find()).as("the login page imports a module").isTrue();
        return found.group(1).replace("\\/", "/");
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class InDevelopment extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void eachModuleIsServedOnItsOwn() throws Exception {
            String module = importedFrom(mockMvc);

            assertThat(module).isEqualTo("/js/form-validation.js");
            assertThat(mockMvc.perform(get(module)).andReturn().getResponse().getContentAsString())
                    .contains("export function initFormValidation");
        }
    }

    @Nested
    @SpringBootTest(properties = "springdrop.assets.aggregate=true")
    @AutoConfigureMockMvc
    class InProduction extends AbstractIntegrationTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void theAggregatedFingerprintedBundleIsServedForAYear() throws Exception {
            String bundle = importedFrom(mockMvc);

            MockHttpServletResponse served = mockMvc.perform(get(bundle)).andReturn().getResponse();

            assertThat(bundle).matches("/assets/springdrop\\.[0-9a-f]{16}\\.js");
            assertThat(served.getStatus()).isEqualTo(200);
            assertThat(served.getHeader("Cache-Control")).contains("max-age=31536000").contains("immutable");
            assertThat(served.getContentAsString()).contains("initFormValidation").contains("initEditors")
                    .doesNotContain("\n  ");
        }
    }
}
