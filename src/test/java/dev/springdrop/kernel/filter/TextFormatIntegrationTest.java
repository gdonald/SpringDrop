package dev.springdrop.kernel.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.filter.filters.HtmlRestrictorFilter;
import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionRegistry;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest
class TextFormatIntegrationTest extends AbstractIntegrationTest {

    private static final String SCRIPTED = "<p onmouseover=\"steal()\">Hello <script>steal()</script>"
            + "<iframe src=\"https://example.com\"></iframe><u>there</u></p>";

    @Autowired
    private TextFormatManager formats;

    @Autowired
    private RoleManager roles;

    @Autowired
    private DefaultTextFormatAccess defaultAccess;

    @Autowired
    private PermissionRegistry permissions;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void theDefaultFormatsWithTheirDefaultAccess() {
        clearGrants();
        formats.installDefaults();
        defaultAccess.grant();
    }

    @AfterEach
    void noGrantsLeft() {
        clearGrants();
        configStore.listNames(TextFormat.CONFIG_PREFIX).stream()
                .filter(name -> name.endsWith(".notes")).forEach(configStore::delete);
        formats.installDefaults();
    }

    private void clearGrants() {
        for (String role : List.of(RoleConfig.ANONYMOUS, RoleConfig.AUTHENTICATED)) {
            roles.find(role).ifPresent(found -> found.permissions().forEach(granted -> roles.revoke(role, granted)));
        }
    }

    private Authentication holdingRoles(long id, String... granted) {
        List<String> held = roles.permissionsOf(List.of(RoleConfig.AUTHENTICATED));
        List<String> all = new java.util.ArrayList<>(held);
        all.addAll(List.of(granted));
        AccountPrincipal principal = new AccountPrincipal(id, "account" + id, "", true, all);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private Authentication anonymous() {
        List<SimpleGrantedAuthority> held = roles.permissionsOf(List.of(RoleConfig.ANONYMOUS)).stream()
                .map(SimpleGrantedAuthority::new).toList();
        List<SimpleGrantedAuthority> withRole = new java.util.ArrayList<>(held);
        withRole.add(new SimpleGrantedAuthority("ROLE_ANONYMOUS"));
        return new AnonymousAuthenticationToken("key", "anonymousUser", withRole);
    }

    private static List<String> ids(List<TextFormat> list) {
        return list.stream().map(TextFormat::id).toList();
    }

    @Test
    void aRestrictedFormatStripsDisallowedTagsAndOnlyItsRolesMayUseIt() {
        assertThat(formats.process("<p>Open <em>daily</em> <u>now</u></p>", TextFormatManager.RESTRICTED_HTML))
                .isEqualTo("<p>Open <em>daily</em> now</p>");
        assertThat(ids(formats.formatsFor(anonymous())))
                .containsExactly(TextFormatManager.RESTRICTED_HTML, TextFormatManager.PLAIN_TEXT);
        assertThat(ids(formats.formatsFor(holdingRoles(7L))))
                .containsExactly(TextFormatManager.BASIC_HTML, TextFormatManager.PLAIN_TEXT);
        assertThat(formats.mayUse(formats.find(TextFormatManager.FULL_HTML).orElseThrow(), holdingRoles(7L)))
                .isFalse();
    }

    @Test
    void scriptsAreStrippedOnRenderEvenInAFormatThatAllowsAnyMarkup() {
        assertThat(formats.process(SCRIPTED, TextFormatManager.FULL_HTML)).isEqualTo("<p>Hello <u>there</u></p>");
    }

    @Test
    void scriptsAreStrippedEvenWhenAFormatsAllowedTagsWereChangedToNameThem() {
        TextFormat basic = formats.find(TextFormatManager.BASIC_HTML).orElseThrow();
        formats.save(basic.withFilters(List.of(new FilterConfig(HtmlRestrictorFilter.ID, 0,
                Map.of(HtmlRestrictorFilter.ALLOWED_HTML, "<p onmouseover> <script> <iframe src> <u>")))));
        try {
            assertThat(formats.process(SCRIPTED, TextFormatManager.BASIC_HTML)).isEqualTo("<p>Hello <u>there</u></p>");
            assertThat(formats.allowedHtml(formats.find(TextFormatManager.BASIC_HTML).orElseThrow()).tagNames())
                    .containsExactly("p", "u");
        } finally {
            formats.save(basic);
        }
    }

    @Test
    void plainTextShowsTagsAsWrittenAndTurnsBreaksAndAddressesIntoHtml() {
        assertThat(formats.process("<b>Hours</b>\nsee https://example.com", TextFormatManager.PLAIN_TEXT))
                .isEqualTo("<p>&lt;b&gt;Hours&lt;/b&gt;<br />\nsee <a href=\"https://example.com\">"
                        + "https://example.com</a></p>");
    }

    @Test
    void textInAFormatTheSiteNoLongerHasIsShownTheWayTheFallbackShowsIt() {
        assertThat(formats.process("<b>Hours</b>", "retired")).isEqualTo("<p>&lt;b&gt;Hours&lt;/b&gt;</p>");
    }

    @Test
    void aFilterTheSiteNoLongerHasIsPassedOver() {
        formats.save(new TextFormat("notes", "Notes", 5, false, List.of(FilterConfig.of("retired_filter", 0))));

        assertThat(formats.process("<p>Kept</p>", "notes")).isEqualTo("<p>Kept</p>");
        assertThat(formats.allowedHtml(formats.find("notes").orElseThrow())).isEqualTo(AllowedHtml.BROAD);
    }

    @Test
    void theFallbackIsOpenToEveryoneAndEveryFormatToAnAdministratorOrTheFirstAccount() {
        TextFormat plain = formats.find(TextFormatManager.PLAIN_TEXT).orElseThrow();
        TextFormat full = formats.find(TextFormatManager.FULL_HTML).orElseThrow();

        assertThat(formats.mayUse(plain, null)).isTrue();
        assertThat(formats.mayUse(full, null)).isFalse();
        assertThat(formats.mayUse(full, holdingRoles(7L, TextFormatManager.ADMINISTER_FILTERS))).isTrue();
        assertThat(formats.mayUse(full, holdingRoles(UserAccount.ADMINISTRATOR_ID))).isTrue();
    }

    @Test
    void eachFormatButTheFallbackHasAPermissionRestrictedWhenItKeepsAnyMarkup() {
        assertThat(permissions.providedBy(TextFormatPermissions.PROVIDER))
                .extracting(PermissionDefinition::name, PermissionDefinition::restricted)
                .contains(tuple(TextFormatManager.permission(TextFormatManager.BASIC_HTML),
                                false),
                        tuple(TextFormatManager.permission(TextFormatManager.FULL_HTML),
                                true))
                .noneMatch(tuple -> tuple.toList().getFirst().equals(
                        TextFormatManager.permission(TextFormatManager.PLAIN_TEXT)));
    }

    @Test
    void addingTheDefaultsAgainLeavesAChangedFormatAsItWas() {
        TextFormat full = formats.find(TextFormatManager.FULL_HTML).orElseThrow();
        formats.save(full.withLabel("Everything"));
        try {
            formats.installDefaults();

            assertThat(formats.find(TextFormatManager.FULL_HTML)).map(TextFormat::label).contains("Everything");
        } finally {
            formats.save(full);
        }
    }

    @Test
    void aSiteWithoutAFallbackFormatSaysSo() {
        TextFormat plain = formats.find(TextFormatManager.PLAIN_TEXT).orElseThrow();
        formats.delete(TextFormatManager.PLAIN_TEXT);
        try {
            assertThatThrownBy(formats::fallback).isInstanceOf(IllegalStateException.class);
        } finally {
            formats.save(plain);
        }
    }

    @Test
    void formatsAreListedLightestFirst() {
        assertThat(ids(formats.all())).containsExactly(TextFormatManager.BASIC_HTML,
                TextFormatManager.RESTRICTED_HTML, TextFormatManager.FULL_HTML, TextFormatManager.PLAIN_TEXT);
        assertThat(formats.find(TextFormatManager.FULL_HTML).orElseThrow().withWeight(9).weight()).isEqualTo(9);
    }
}
