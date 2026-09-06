package dev.springdrop.kernel.ban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.web.IpBanController;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class IpBanIntegrationTest extends AbstractIntegrationTest {

    private static final String LOOPBACK = "127.0.0.1";

    private static final String OFFICE = "10.0.0.5";

    @Autowired
    private IpBanService bans;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void nobodyIsBanned() {
        bans.banned().forEach(bans::unban);
    }

    @Test
    void aBannedAddressIsRefusedBeforeRoutingAndUnbanningLetsItBackIn() throws Exception {
        bans.ban(LOOPBACK);

        mockMvc.perform(get("/"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(IpBanFilter.MESSAGE));

        bans.unban(LOOPBACK);

        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void anAddressNobodyBannedIsAnswered() throws Exception {
        bans.ban(OFFICE);

        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void banningTheSameAddressTwiceHoldsItOnce() {
        bans.ban(OFFICE);
        bans.ban(OFFICE);

        assertThat(bans.banned()).containsExactly(OFFICE);
    }

    @Test
    void anAddressGivenWithSpaceAroundItIsStoredWithout() {
        bans.ban("  " + OFFICE + " ");

        assertThat(bans.banned()).containsExactly(OFFICE);
    }

    @Test
    void banningNothingAtAllBansNothing() {
        bans.ban(null);
        bans.ban("   ");

        assertThat(bans.banned()).isEmpty();
    }

    @Test
    void unbanningAnAddressNobodyBannedLeavesTheListAsItWas() {
        bans.ban(OFFICE);

        bans.unban("192.0.2.7");

        assertThat(bans.banned()).containsExactly(OFFICE);
    }

    @Test
    void anAdministratorBansAnAddressAndSeesItListed() throws Exception {
        mockMvc.perform(post(IpBanController.PATH)
                        .param(IpBanController.ADDRESS, OFFICE)
                        .with(banAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Document document = Jsoup.parse(mockMvc.perform(get(IpBanController.PATH)
                        .with(banAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select("tbody td")).extracting(element -> element.text())
                .contains(OFFICE);
    }

    @Test
    void anAdministratorLetsABannedAddressBackIn() throws Exception {
        bans.ban(OFFICE);

        mockMvc.perform(post(IpBanController.UNBAN_PATH)
                        .param(IpBanController.ADDRESS, OFFICE)
                        .with(banAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(bans.banned()).isEmpty();
    }

    @Test
    void anEmptyListSaysSo() throws Exception {
        Document document = Jsoup.parse(mockMvc.perform(get(IpBanController.PATH)
                        .with(banAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(document.select("main p")).extracting(element -> element.text())
                .contains("No addresses are banned.");
    }

    @Test
    void theBanListIsClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(IpBanController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor banAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.BAN_IP_ADDRESSES));
    }
}
