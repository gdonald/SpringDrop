package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.datetime.SiteDateSettings;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.DateRangeFieldType;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.site.RssSettings;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class RegionalAndRssSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT = "event";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigStore config;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private DefaultViews defaultViews;

    @BeforeEach
    void eventsWithDates() {
        clear();
        defaultViews.install();
        types.save(NodeType.of(EVENT, "Event"));
        fields.createStorage(new FieldStorageConfig("starts_at", NodeEntityType.ID, DateTimeFieldType.ID, 1,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_AND_TIME)));
        fields.createInstance(FieldInstanceConfig.of("starts_at", NodeEntityType.ID, EVENT, "Starts at"));
        fields.createStorage(new FieldStorageConfig("runs", NodeEntityType.ID, DateRangeFieldType.ID, 1,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_ONLY)));
        fields.createInstance(FieldInstanceConfig.of("runs", NodeEntityType.ID, EVENT, "Runs"));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        config.delete(SiteDateSettings.CONFIG_NAME);
        config.delete(RssSettings.CONFIG_NAME);
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        List.of("starts_at", "runs").forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        types.find(EVENT).ifPresent(type -> types.delete(EVENT));
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(send(request).getContentAsString());
    }

    private Document feed() throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/rss.xml").with(user(new AccountPrincipal(7L, "reader", "", true,
                List.of(NodePermissions.ACCESS_CONTENT))))).andReturn().getResponse().getContentAsString(),
                Parser.xmlParser());
    }

    private Document widget(String field) {
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(widgets.context(NodeEntityType.ID, EVENT, field),
                List.of(), 0)));
    }

    @Test
    void theRssItemCountLimitsFeedLength() throws Exception {
        for (int place = 1; place <= 5; place++) {
            nodes.save(EntityData.of(NodeEntityType.ID, null, EVENT, "Concert " + place, Map.of(
                    BaseFieldDefinition.STATUS, true, NodeEntityType.PROMOTE, true)), 1L);
        }
        assertThat(feed().select("item")).hasSize(5);
        assertThat(feed().selectFirst("channel > description").text()).isNotBlank();

        MockHttpServletResponse saved = send(post(RssSettingsController.PATH)
                .param(RssSettingsController.ITEMS, "3")
                .param(RssSettingsController.DESCRIPTION, " Concerts and events in town. "));

        assertThat(saved.getRedirectedUrl()).isEqualTo(RssSettingsController.PATH);
        assertThat(feed().select("item")).hasSize(3);
        assertThat(feed().selectFirst("channel > description").text()).isEqualTo("Concerts and events in town.");
    }

    @Test
    void theFirstDayOfWeekSettingChangesDateWidgets() throws Exception {
        assertThat(widget("starts_at").selectFirst("input[type=datetime-local]").attr("data-first-day"))
                .isEqualTo("0");

        send(post(RegionalSettingsController.PATH)
                .param(RegionalSettingsController.COUNTRY, "US")
                .param(RegionalSettingsController.FIRST_DAY, DayOfWeek.MONDAY.name())
                .param(RegionalSettingsController.TIMEZONE, "America/Chicago"));

        assertThat(widget("starts_at").selectFirst("input[type=datetime-local]").attr("data-first-day"))
                .isEqualTo("1");
        assertThat(widget("runs").select("input[type=date]")).allSatisfy(input ->
                assertThat(input.attr("data-first-day")).isEqualTo("1"));
        assertThat(config.read(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class, null))
                .isEqualTo(new SiteDateSettings("America/Chicago", "US", DayOfWeek.MONDAY));
    }

    @Test
    void theRegionalFormShowsTheSettingsAndRefusesChoicesThatAreNotOffered() throws Exception {
        config.save(SiteDateSettings.CONFIG_NAME, new SiteDateSettings("Europe/Athens", "GR", DayOfWeek.SATURDAY));

        Document form = page(get(RegionalSettingsController.PATH));
        assertThat(form.selectFirst("select[name=country] option[selected]").val()).isEqualTo("GR");
        assertThat(form.selectFirst("select[name=first_day] option[selected]").val()).isEqualTo("SATURDAY");
        assertThat(form.select("select[name=first_day] option").first().text()).isEqualTo("Sunday");

        Document refused = page(post(RegionalSettingsController.PATH)
                .param(RegionalSettingsController.COUNTRY, "XX")
                .param(RegionalSettingsController.FIRST_DAY, "SOMEDAY")
                .param(RegionalSettingsController.TIMEZONE, "Mars/Olympus_Mons"));
        assertThat(refused.text()).contains("Choose a country.", "Choose a day.", "Choose a timezone.");
        assertThat(refused.selectFirst(".alert-success")).isNull();

        send(post(RegionalSettingsController.PATH).param(RegionalSettingsController.COUNTRY, "")
                .param(RegionalSettingsController.FIRST_DAY, "SUNDAY")
                .param(RegionalSettingsController.TIMEZONE, "UTC"));
        assertThat(config.read(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class, null).country()).isEmpty();
    }

    @Test
    void theRssFormShowsTheSettingsAndRefusesACountOutOfRange() throws Exception {
        assertThat(page(get(RssSettingsController.PATH)).selectFirst("input[name=feed_items]").val())
                .isEqualTo("10");

        Document refused = page(post(RssSettingsController.PATH).param(RssSettingsController.ITEMS, "500"));
        assertThat(refused.select(".invalid-feedback")).isNotEmpty();
        Document notANumber = page(post(RssSettingsController.PATH).param(RssSettingsController.ITEMS, "many"));
        assertThat(notANumber.text()).contains("Enter a whole number.");
        assertThat(config.read(RssSettings.CONFIG_NAME, RssSettings.class, null)).isNull();
    }

    @Test
    void oldSettingsWithoutTheNewKeysReadWithTheirDefaults() {
        config.save(SiteDateSettings.CONFIG_NAME, Map.of("defaultTimezone", "UTC"));
        config.save(RssSettings.CONFIG_NAME, Map.of("items", 4));

        assertThat(config.read(SiteDateSettings.CONFIG_NAME, SiteDateSettings.class, null))
                .isEqualTo(new SiteDateSettings("UTC"));
        assertThat(config.read(RssSettings.CONFIG_NAME, RssSettings.class, null)).isEqualTo(new RssSettings(4, ""));
    }

    @Test
    void theFormsNeedTheSiteConfigurationPermission() throws Exception {
        assertThat(mockMvc.perform(get(RegionalSettingsController.PATH).with(user("editor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(get(RssSettingsController.PATH).with(user("editor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }
}
