package dev.springdrop.kernel.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MenuLinkTest {

    private static final MenuLink NORWAY = MenuLink.of("travel.norway", "travel", "Norway", "/norway");

    @Test
    void aNewLinkHangsAtTheTopOfItsMenuAndIsOn() {
        assertThat(NORWAY.parent()).isNull();
        assertThat(NORWAY.weight()).isZero();
        assertThat(NORWAY.enabled()).isTrue();
        assertThat(NORWAY.expanded()).isFalse();
        assertThat(NORWAY.description()).isEmpty();
        assertThat(NORWAY.requiredPermission()).isNull();
    }

    @Test
    void hangingALinkUnderAnotherNamesThatOneAsItsParent() {
        assertThat(NORWAY.under("travel.europe").parent()).isEqualTo("travel.europe");
    }

    @Test
    void aLighterLinkComesFirst() {
        assertThat(NORWAY.withWeight(-5).weight()).isEqualTo(-5);
    }

    @Test
    void aLinkCanCarryTheHelpTextShownBesideIt() {
        assertThat(NORWAY.withDescription("The coast road.").description()).isEqualTo("The coast road.");
    }

    @Test
    void aLinkCanShowItsChildrenWithoutBeingOpenedFirst() {
        assertThat(NORWAY.expandedByDefault().expanded()).isTrue();
    }

    @Test
    void turningALinkOffLeavesItInTheMenuButOutOfTheTree() {
        assertThat(NORWAY.disabled().enabled()).isFalse();
    }

    @Test
    void aLinkCanNameThePermissionItNeeds() {
        assertThat(NORWAY.requiring("read the ledger").requiredPermission()).isEqualTo("read the ledger");
    }

    @Test
    void aMenuNamesTheConfigObjectAndCacheTagItIsKnownBy() {
        assertThat(MenuConfig.configName("travel")).isEqualTo("system.menu.travel");
        assertThat(MenuConfig.cacheTag("travel")).isEqualTo("menu:travel");
    }

    @Test
    void aMenuTheSiteReliesOnIsLocked() {
        assertThat(MenuConfig.of("travel", "Travel", "Trips.").locked()).isFalse();
        assertThat(MenuConfig.of("travel", "Travel", "Trips.").asLocked().locked()).isTrue();
    }
}
