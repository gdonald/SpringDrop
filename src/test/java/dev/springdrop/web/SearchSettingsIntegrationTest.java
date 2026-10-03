package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.search.LuceneSearchBackend;
import dev.springdrop.kernel.search.PostgresSearchBackend;
import dev.springdrop.kernel.search.SearchService;
import dev.springdrop.kernel.search.SearchSettings;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class SearchSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SearchService search;

    @Autowired
    private LuceneSearchBackend lucene;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private UserAccountService accounts;

    @BeforeEach
    void accountsInstalled() {
        accounts.install();
    }

    @AfterEach
    void backToPostgres() {
        configStore.delete(SearchSettings.CONFIG_NAME);
        lucene.clear(NodeEntityType.ID);
        lucene.clear(UserEntityType.ID);
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(SearchSettingsController.ADMINISTER_SEARCH));
    }

    private MockHttpServletResponse choose(String backend) throws Exception {
        return mockMvc.perform(post(SearchSettingsController.PATH).param(SearchSettingsController.BACKEND, backend)
                .with(csrf()).with(administrator())).andReturn().getResponse();
    }

    @Test
    void theFormOffersEachBackendWithTheCurrentOneChosen() throws Exception {
        Document form = Jsoup.parse(mockMvc.perform(get(SearchSettingsController.PATH).with(administrator()))
                .andReturn().getResponse().getContentAsString());

        assertThat(form.select("select[name=backend] option").eachAttr("value")).containsExactly(
                LuceneSearchBackend.ID, PostgresSearchBackend.ID);
        assertThat(form.selectFirst("select[name=backend] option[selected]").val())
                .isEqualTo(PostgresSearchBackend.ID);
        assertThat(form.text()).contains("The site searches through postgres.");
    }

    @Test
    void savingABackendSwitchesToItAndAnUnknownOneIsRefused() throws Exception {
        assertThat(choose(LuceneSearchBackend.ID).getRedirectedUrl()).isEqualTo(SearchSettingsController.PATH);
        assertThat(search.backend().id()).isEqualTo(LuceneSearchBackend.ID);

        assertThat(choose("solr").getContentAsString()).contains("Choose a backend.");
        assertThat(choose("").getContentAsString()).contains("This value is required.");
        assertThat(search.backend().id()).isEqualTo(LuceneSearchBackend.ID);
    }

    @Test
    void theSettingsAreClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(SearchSettingsController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
