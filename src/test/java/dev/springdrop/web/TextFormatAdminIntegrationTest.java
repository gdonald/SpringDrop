package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.filter.FilterConfig;
import dev.springdrop.kernel.filter.TextFormat;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.filter.filters.HtmlCorrectorFilter;
import dev.springdrop.kernel.filter.filters.HtmlRestrictorFilter;
import dev.springdrop.kernel.filter.filters.UrlFilter;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class TextFormatAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String EDITOR = "editor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TextFormatManager formats;

    @Autowired
    private RoleManager roles;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void theDefaultFormatsAndAnEditorRole() {
        roles.install();
        formats.installDefaults();
        roles.save(RoleConfig.of(EDITOR, "Editor", 3));
    }

    @AfterEach
    void onlyTheDefaultsLeft() {
        roles.delete(EDITOR);
        configStore.listNames(TextFormat.CONFIG_PREFIX).forEach(configStore::delete);
        formats.installDefaults();
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(TextFormatManager.ADMINISTER_FILTERS));
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(administrator()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void theFormatsAreListedWithTheRolesThatMayUseThem() throws Exception {
        roles.grant(EDITOR, TextFormatManager.permission(TextFormatManager.FULL_HTML));

        Document document = page(get(TextFormatController.PATH));

        assertThat(document.selectFirst("[data-format=full_html]").text()).contains("Full HTML", "Editor");
        assertThat(document.selectFirst("[data-format=basic_html]").text()).contains("No roles");
        assertThat(document.selectFirst("[data-format=plain_text]").text()).contains("All roles");
        assertThat(document.select("[data-format=plain_text] a:contains(Delete)")).isEmpty();
        BootstrapAssertions.assertNoOutlineButtons(document);
        BootstrapAssertions.assertEditControlsAreButtons(document);
        mockMvc.perform(get(TextFormatController.PATH).with(user("visitor"))).andExpect(status().isForbidden());
    }

    @Test
    void aFormatIsAddedWithItsRolesFiltersAndSettings() throws Exception {
        assertThat(page(get(TextFormatController.PATH + "/add"))
                .select("input[name=" + TextFormatController.ROLE_PREFIX + EDITOR + "]")).isNotEmpty();
        String restrictor = TextFormatController.filterPrefix(HtmlRestrictorFilter.ID);

        mockMvc.perform(post(TextFormatController.PATH + "/add").with(administrator()).with(csrf())
                        .param(TextFormatController.LABEL, "Notes")
                        .param(TextFormatController.WEIGHT, "4")
                        .param(TextFormatController.ROLE_PREFIX + EDITOR, "true")
                        .param(restrictor + TextFormatController.ENABLED, "true")
                        .param(restrictor + TextFormatController.WEIGHT, "-5")
                        .param(restrictor + HtmlRestrictorFilter.ALLOWED_HTML, "<p> <em>")
                        .param(TextFormatController.filterPrefix(HtmlCorrectorFilter.ID) + TextFormatController.ENABLED,
                                "true"))
                .andExpect(redirectedUrl(TextFormatController.PATH));

        assertThat(formats.find("notes")).hasValueSatisfying(format -> {
            assertThat(format.weight()).isEqualTo(4);
            assertThat(format.filters()).extracting(FilterConfig::id)
                    .containsExactly(HtmlRestrictorFilter.ID, HtmlCorrectorFilter.ID);
            assertThat(format.filter(HtmlRestrictorFilter.ID).orElseThrow().settings())
                    .containsEntry(HtmlRestrictorFilter.ALLOWED_HTML, "<p> <em>");
        });
        assertThat(roles.find(EDITOR).orElseThrow().permissions()).contains(TextFormatManager.permission("notes"));
        assertThat(formats.process("<p>Kept <u>dropped</u></p>", "notes")).isEqualTo("<p>Kept dropped</p>");
    }

    @Test
    void aFormatWithoutANameOrWithABadWeightIsNotSaved() throws Exception {
        Document document = page(post(TextFormatController.PATH + "/add").with(csrf())
                .param(TextFormatController.LABEL, "")
                .param(TextFormatController.filterPrefix(UrlFilter.ID) + UrlFilter.LENGTH, "long"));

        assertThat(document.select(".is-invalid").eachAttr("name")).containsExactlyInAnyOrder(
                TextFormatController.LABEL, TextFormatController.filterPrefix(UrlFilter.ID) + UrlFilter.LENGTH);
        assertThat(formats.all()).hasSize(4);
    }

    @Test
    void editingAFormatShowsItsFiltersAndTakesARoleAway() throws Exception {
        roles.grant(EDITOR, TextFormatManager.permission(TextFormatManager.FULL_HTML));
        Document form = page(get(TextFormatController.managePath(TextFormatManager.FULL_HTML)));
        assertThat(form.selectFirst("input[name=" + TextFormatController.ROLE_PREFIX + EDITOR + "]")
                .hasAttr("checked")).isTrue();
        assertThat(form.selectFirst("input[name=" + TextFormatController.filterPrefix(HtmlCorrectorFilter.ID)
                + TextFormatController.ENABLED + "]").hasAttr("checked")).isTrue();

        mockMvc.perform(post(TextFormatController.managePath(TextFormatManager.FULL_HTML)).with(administrator())
                        .with(csrf())
                        .param(TextFormatController.LABEL, "Everything"))
                .andExpect(redirectedUrl(TextFormatController.PATH));

        assertThat(formats.find(TextFormatManager.FULL_HTML)).hasValueSatisfying(format -> {
            assertThat(format.label()).isEqualTo("Everything");
            assertThat(format.filters()).isEmpty();
        });
        assertThat(roles.find(EDITOR).orElseThrow().permissions())
                .doesNotContain(TextFormatManager.permission(TextFormatManager.FULL_HTML));
    }

    @Test
    void theFallbackFormatHasNoRolesToChooseAndCannotBeDeleted() throws Exception {
        assertThat(page(get(TextFormatController.managePath(TextFormatManager.PLAIN_TEXT)))
                .select("input[name^=" + TextFormatController.ROLE_PREFIX + "]")).isEmpty();
        mockMvc.perform(post(TextFormatController.managePath(TextFormatManager.PLAIN_TEXT)).with(administrator())
                        .with(csrf()).param(TextFormatController.LABEL, "Plain"))
                .andExpect(status().is3xxRedirection());

        assertThat(formats.find(TextFormatManager.PLAIN_TEXT)).map(TextFormat::fallback).contains(true);
        mockMvc.perform(get(TextFormatController.managePath(TextFormatManager.PLAIN_TEXT) + "/delete")
                .with(administrator())).andExpect(status().isNotFound());
    }

    @Test
    void aFormatIsDeletedOnceConfirmedAndItsPermissionTakenFromEveryRole() throws Exception {
        roles.grant(EDITOR, TextFormatManager.permission(TextFormatManager.FULL_HTML));
        assertThat(page(get(TextFormatController.managePath(TextFormatManager.FULL_HTML) + "/delete"))
                .text()).contains("shown the way Plain text shows text");

        mockMvc.perform(post(TextFormatController.managePath(TextFormatManager.FULL_HTML) + "/delete")
                .with(administrator()).with(csrf())).andExpect(redirectedUrl(TextFormatController.PATH));

        assertThat(formats.find(TextFormatManager.FULL_HTML)).isEmpty();
        assertThat(roles.find(EDITOR).orElseThrow().permissions())
                .doesNotContain(TextFormatManager.permission(TextFormatManager.FULL_HTML));
        mockMvc.perform(get(TextFormatController.managePath(TextFormatManager.FULL_HTML)).with(administrator()))
                .andExpect(status().isNotFound());
    }
}
