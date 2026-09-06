package dev.springdrop.kernel.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.ArrayList;
import java.util.List;
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
class ConfirmFormIntegrationTest extends AbstractIntegrationTest {

    static final String DELETE_FORM = "delete_article";

    private static final String PATH = "/form/" + DELETE_FORM;

    @Autowired
    private FormBuilder forms;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeleteArticleForm deleteForm;

    @BeforeEach
    void nothingDeletedYet() {
        deleteForm.deleted.clear();
    }

    private Document rendered() {
        return Jsoup.parseBodyFragment(forms.render(DELETE_FORM));
    }

    @Test
    void theQuestionAndWhatWillHappenAreBothShown() {
        Document document = rendered();

        assertThat(document.selectFirst("p.h5").text()).isEqualTo("Delete the article Hello?");
        assertThat(document.selectFirst("p.text-body-secondary").text())
                .isEqualTo("This action cannot be undone.");
    }

    @Test
    void thereIsOneConfirmButtonAndAWayBack() {
        Document document = rendered();

        assertThat(document.select("button[type=submit]")).singleElement()
                .satisfies(button -> assertThat(button.text()).isEqualTo("Delete"));
        assertThat(document.selectFirst("a").text()).isEqualTo("Cancel");
        assertThat(document.selectFirst("a").attr("href")).isEqualTo("/articles");
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void showingTheFormDeletesNothing() throws Exception {
        mockMvc.perform(get(PATH)).andExpect(status().isOk());

        assertThat(deleteForm.deleted).isEmpty();
    }

    @Test
    void onlyTheConfirmingPostCarriesTheActionOut() throws Exception {
        mockMvc.perform(post(PATH).param(ConfirmForm.CONFIRM, "Delete").with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(deleteForm.deleted).containsExactly("Hello");
    }

    @Test
    void aFormThatSaysNothingMoreUsesTheDefaultWordingAndButton() {
        Document document = Jsoup.parseBodyFragment(forms.render(PlainConfirmForm.ID));

        assertThat(document.selectFirst("p.text-body-secondary").text())
                .isEqualTo("This action cannot be undone.");
        assertThat(document.selectFirst("button").text()).isEqualTo("Confirm");
    }

    @TestConfiguration
    static class Forms {

        @Bean
        DeleteArticleForm deleteArticleForm() {
            return new DeleteArticleForm();
        }

        @Bean
        PlainConfirmForm plainConfirmForm() {
            return new PlainConfirmForm();
        }
    }

    /** Deleting an article, with its own question and button wording. */
    static class DeleteArticleForm extends ConfirmForm {

        final List<String> deleted = new ArrayList<>();

        @Override
        public String id() {
            return DELETE_FORM;
        }

        @Override
        protected String question() {
            return "Delete the article Hello?";
        }

        @Override
        protected String cancelPath() {
            return "/articles";
        }

        @Override
        protected String confirmLabel() {
            return "Delete";
        }

        @Override
        protected void confirmed(FormState state) {
            deleted.add("Hello");
        }
    }

    /** A confirm form that says no more than it has to. */
    static class PlainConfirmForm extends ConfirmForm {

        static final String ID = "plain_confirm";

        @Override
        public String id() {
            return ID;
        }

        @Override
        protected String question() {
            return "Are you sure?";
        }

        @Override
        protected String cancelPath() {
            return "/";
        }

        @Override
        protected void confirmed(FormState state) {
            // Nothing to do: this form exists to show the defaults.
        }
    }
}
