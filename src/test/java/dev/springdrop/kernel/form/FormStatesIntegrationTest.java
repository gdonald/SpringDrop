package dev.springdrop.kernel.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.web.FormController;
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
class FormStatesIntegrationTest extends AbstractIntegrationTest {

    static final String DELIVERY_FORM = "delivery";

    private static final String PATH = "/form/" + DELIVERY_FORM;

    @Autowired
    private FormBuilder forms;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryForm deliveryForm;

    @BeforeEach
    void nothingSubmittedYet() {
        deliveryForm.submissions.clear();
    }

    @Test
    void aConditionalElementCarriesItsConditionForTheScript() {
        Document document = Jsoup.parseBodyFragment(forms.render(DELIVERY_FORM));

        assertThat(document.selectFirst("input#address").attr("data-state-visible"))
                .isEqualTo("method:post");
        assertThat(document.selectFirst("input#address").attr("data-state-required"))
                .isEqualTo("method:post");
    }

    @Test
    void aHiddenRequiredElementIsNotEnforced() {
        FormOutcome outcome = forms.handle(DELIVERY_FORM, Map.of("method", "collect", "address", ""));

        assertThat(outcome.submitted()).isTrue();
        assertThat(deliveryForm.submissions).containsExactly("collect to ");
    }

    @Test
    void theSameElementIsEnforcedOnceItsConditionHolds() {
        FormOutcome outcome = forms.handle(DELIVERY_FORM, Map.of("method", "post", "address", ""));

        assertThat(outcome.submitted()).isFalse();
        assertThat(outcome.state().errors()).containsEntry("address", "This value is required.");
    }

    @Test
    void anAlwaysRequiredElementIsEnforcedWhicheverWayTheConditionsGo() {
        FormOutcome outcome = forms.handle(DELIVERY_FORM, Map.of("method", "", "address", ""));

        assertThat(outcome.state().errors()).containsEntry("method", "This value is required.");
    }

    @Test
    void theFormPageRendersTheFormWithItsScript() throws Exception {
        String page = mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document document = Jsoup.parse(page);
        assertThat(document.selectFirst("form[data-form-states]")).isNotNull();
        assertThat(document.selectFirst("input#method")).isNotNull();
        assertThat(page).contains("form-states.js");
    }

    @Test
    void aFullPagePostWithErrorsRendersTheWholePageAgain() throws Exception {
        String page = mockMvc.perform(post(PATH).param("method", "post").param("address", "").with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Document document = Jsoup.parse(page);
        assertThat(document.selectFirst("html")).isNotNull();
        assertThat(document.selectFirst("input#address").hasClass("is-invalid")).isTrue();
    }

    @Test
    void anHtmxPostWithErrorsReturnsJustTheForm() throws Exception {
        String fragment = mockMvc.perform(post(PATH)
                        .header("HX-Request", "true")
                        .param("method", "post")
                        .param("address", "")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andReturn().getResponse().getContentAsString();

        assertThat(fragment).doesNotContain("<html");
        assertThat(Jsoup.parseBodyFragment(fragment).selectFirst("input#address").hasClass("is-invalid"))
                .isTrue();
    }

    @Test
    void aFullPagePostThatSubmitsGoesBackToTheForm() throws Exception {
        mockMvc.perform(post(PATH).param("method", "collect").param("address", "").with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(deliveryForm.submissions).containsExactly("collect to ");
    }

    @Test
    void anHtmxPostThatSubmitsTellsTheBrowserWhereToGo() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("HX-Request", "true")
                        .param("method", "collect")
                        .param("address", "")
                        .with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("HX-Redirect", PATH));

        assertThat(deliveryForm.submissions).containsExactly("collect to ");
    }

    @Test
    void theSubmittedValuesLeaveOutTheSecurityToken() throws Exception {
        mockMvc.perform(post(PATH).param("method", "collect").param("address", "").with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(deliveryForm.lastValues).doesNotContainKey("_csrf");
    }

    @Test
    void theFormPathIsTheOneTheControllerServes() {
        assertThat(FormController.PATH.replace("{formId}", DELIVERY_FORM)).isEqualTo(PATH);
    }

    @TestConfiguration
    static class Forms {

        @Bean
        DeliveryForm deliveryForm() {
            return new DeliveryForm();
        }
    }

    /** An address that is asked for only when the goods are being posted. */
    static class DeliveryForm implements Form {

        final List<String> submissions = new ArrayList<>();

        Map<String, Object> lastValues = Map.of();

        @Override
        public String id() {
            return DELIVERY_FORM;
        }

        @Override
        public FormElement build(FormState state) {
            return FormElement.of(ElementType.CONTAINER, "delivery")
                    .child(FormElement.of(ElementType.TEXTFIELD, "method").label("Method").markRequired())
                    .child(FormElement.of(ElementType.TEXTFIELD, "address")
                            .label("Address")
                            .visibleWhen("method", "post")
                            .requiredWhen("method", "post"))
                    .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"));
        }

        @Override
        public void submit(FormState state) {
            lastValues = state.values();
            submissions.add(state.value("method").orElseThrow() + " to " + state.value("address").orElseThrow());
        }
    }
}
