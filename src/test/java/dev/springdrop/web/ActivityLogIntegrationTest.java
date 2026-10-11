package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.log.ActivityLog;
import dev.springdrop.kernel.log.DatabaseLog;
import dev.springdrop.kernel.log.LogFilter;
import dev.springdrop.kernel.log.Severity;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ActivityLogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ActivityLog activityLog;

    @Autowired
    private DatabaseLog database;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @BeforeEach
    void anEmptyLog() {
        accounts.install();
        database.clear();
    }

    private static RequestPostProcessor reporter() {
        return user("reporter").authorities(new SimpleGrantedAuthority(Permissions.ACCESS_SITE_REPORTS));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(reporter())).andReturn().getResponse()
                .getContentAsString());
    }

    private static List<String> messages(Document listing) {
        return listing.select("table.log-events tbody td:nth-child(4)").eachText();
    }

    @Test
    void aLoggedEventAppearsInTheViewerNewestFirst() throws Exception {
        activityLog.log("action", Severity.NOTICE, "Publish content was applied to node {label} (4).",
                Map.of("label", "Spring schedule for the garden club"));
        activityLog.log("user", Severity.ERROR, "Sign-in refused for {name}.", Map.of("name", "ada"));

        Document listing = page(ActivityLogController.PATH);

        assertThat(messages(listing)).containsExactly("Sign-in refused for ada.",
                "Publish content was applied to node Spring schedule for ...");
        assertThat(listing.select("table.log-events tbody td:nth-child(1)").eachText())
                .containsExactly("Error", "Notice");
        assertThat(listing.select("table.log-events tbody td:nth-child(5)").eachText())
                .containsExactly("Anonymous", "Anonymous");
        assertThat(listing.select("select[name=type] option").eachAttr("value")).containsExactly("", "action",
                "user");
        BootstrapAssertions.assertNoOutlineButtons(listing);
    }

    @Test
    void theViewerFiltersBySeverityAndType() throws Exception {
        activityLog.log("action", Severity.NOTICE, "first", Map.of());
        activityLog.log("action", Severity.ERROR, "second", Map.of());
        activityLog.log("user", Severity.ERROR, "third", Map.of());

        Document errors = page(ActivityLogController.PATH + "?severity=3");
        assertThat(messages(errors)).containsExactly("third", "second");
        assertThat(errors.selectFirst("select[name=severity] option[selected]").text()).isEqualTo("Error");

        assertThat(messages(page(ActivityLogController.PATH + "?severity=3&type=action")))
                .containsExactly("second");
        assertThat(messages(page(ActivityLogController.PATH + "?severity=9"))).hasSize(3);
        assertThat(page(ActivityLogController.PATH + "?type=cron").text()).contains("No log messages available.");
    }

    @Test
    void eachEventHasADetailPage() throws Exception {
        activityLog.log("action", Severity.WARNING, "Block account was applied to user {label} (5).",
                Map.of("label", "ada"));
        long id = database.page(LogFilter.ALL, 0, 1).getFirst().id();

        Document listing = page(ActivityLogController.PATH);
        assertThat(listing.selectFirst("table.log-events tbody a").attr("href"))
                .isEqualTo(ActivityLogController.eventPath(id));

        Document detail = page(ActivityLogController.eventPath(id));
        assertThat(detail.select("table.log-event th").eachText()).containsExactly("Type", "Date", "User",
                "Location", "Referrer", "Message", "Severity", "Hostname");
        assertThat(detail.select("table.log-event td").eachText()).contains("action",
                "Block account was applied to user ada (5).", "Warning");
        assertThat(mockMvc.perform(get(ActivityLogController.eventPath(id + 1000)).with(reporter())).andReturn()
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void anEventNamesTheAccountThatCausedIt() throws Exception {
        accounts.findByName("ada").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
        long ada = accounts.create("ada", "ada@example.com", "").id();
        AccountPrincipal signedIn = new AccountPrincipal(ada, "ada", "", true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(signedIn, null, signedIn.getAuthorities()));
        try {
            activityLog.log("content", Severity.INFO, "Edited.", Map.of());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(page(ActivityLogController.PATH).select("table.log-events tbody td:nth-child(5)").eachText())
                .containsExactly("ada");
        entities.delete(UserEntityType.ID, ada);
    }

    @Test
    void theListingIsPagedFiftyAtATimeKeepingTheFilters() throws Exception {
        for (int number = 1; number <= 51; number++) {
            activityLog.log("action", Severity.NOTICE, "event " + number, Map.of());
        }

        Document first = page(ActivityLogController.PATH + "?type=action&severity=5");
        assertThat(messages(first)).hasSize(ActivityLogController.PAGE_SIZE).first().isEqualTo("event 51");
        assertThat(first.select(".pagination a.page-link").eachAttr("href")).contains(
                ActivityLogController.PATH + "?type=action&severity=5&page=2");

        assertThat(messages(page(ActivityLogController.PATH + "?page=2"))).containsExactly("event 1");
        assertThat(messages(page(ActivityLogController.PATH + "?page=99"))).containsExactly("event 1");
        assertThat(messages(page(ActivityLogController.PATH + "?page=x")).getFirst()).isEqualTo("event 51");
        assertThat(page(ActivityLogController.PATH + "?page=2").select(".pagination a.page-link").eachAttr("href"))
                .contains(ActivityLogController.PATH + "?page=1");
    }

    @Test
    void clearingTheLogAsksFirstThenEmptiesIt() throws Exception {
        activityLog.log("action", Severity.NOTICE, "first", Map.of());

        Document confirm = page(ActivityLogController.CLEAR_PATH);
        assertThat(confirm.title()).isEqualTo("Clear the log?");
        assertThat(database.count(LogFilter.ALL)).isEqualTo(1);

        mockMvc.perform(post(ActivityLogController.CLEAR_PATH).with(csrf()).with(reporter()));

        assertThat(database.count(LogFilter.ALL)).isZero();
    }

    @Test
    void eachSeverityHasABadgeColor() {
        assertThat(List.of(Severity.values())).extracting(ActivityLogController::severityClass).containsExactly(
                "text-bg-danger", "text-bg-danger", "text-bg-danger", "text-bg-danger", "text-bg-warning",
                "text-bg-info", "text-bg-secondary", "text-bg-secondary");
    }

    @Test
    void theLogIsClosedWithoutThePermission() throws Exception {
        assertThat(mockMvc.perform(get(ActivityLogController.PATH).with(user("visitor"))).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
        assertThat(mockMvc.perform(post(ActivityLogController.CLEAR_PATH).with(csrf()).with(user("visitor")))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }
}
