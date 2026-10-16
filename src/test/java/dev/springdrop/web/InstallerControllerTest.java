package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.install.InstallChoices;
import dev.springdrop.kernel.install.InstallRequirements;
import dev.springdrop.kernel.install.Installer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.ExtendedModelMap;

/** The installer on a site that does not meet its requirements. */
class InstallerControllerTest {

    private final InstallRequirements requirements = mock(InstallRequirements.class);

    private final FormRenderer renderer = new FormRenderer();

    private final InstallerController controller = new InstallerController(mock(Installer.class), requirements,
            mock(PasswordEncoder.class), new FormBuilder(List.of(), renderer, mock(ApplicationEventPublisher.class)),
            renderer);

    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    void requirementsNotMet() {
        when(requirements.allMet()).thenReturn(false);
        when(requirements.check()).thenReturn(List.of());
        session.setAttribute(InstallerController.CHOICES, InstallChoices.NONE.withLangcode("en"));
    }

    @Test
    void theRequirementsPageSaysTheyAreNotMet() {
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.requirements(session, model)).isEqualTo("install/requirements");
        assertThat(model.get("met")).isEqualTo(false);
    }

    @Test
    void theStepsAfterTheRequirementsSendThePersonBackToThem() {
        String back = "redirect:" + InstallerController.REQUIREMENTS_PATH;

        assertThat(controller.profile(session, new ExtendedModelMap())).isEqualTo(back);
        assertThat(controller.chooseProfile(Map.of(), session, new ExtendedModelMap())).isEqualTo(back);
        assertThat(controller.site(session, new ExtendedModelMap())).isEqualTo(back);
    }
}
