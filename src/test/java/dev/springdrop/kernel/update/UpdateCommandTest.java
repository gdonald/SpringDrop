package dev.springdrop.kernel.update;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class UpdateCommandTest {

    private final UpdateManager updates = mock(UpdateManager.class);

    @Test
    void startingWithTheUpdateOptionRunsThePendingRoutines() {
        UpdateRoutine broken = mock(UpdateRoutine.class);
        when(updates.runPending()).thenReturn(new UpdateManager.Report(List.of(), Optional.of(broken), "Broken."));

        new UpdateCommand(new DefaultApplicationArguments("--update"), updates).run();

        verify(updates).runPending();
    }

    @Test
    void startingWithoutItRunsNothing() {
        new UpdateCommand(new DefaultApplicationArguments(), updates).run();

        verify(updates, never()).runPending();
    }

    @Test
    void aRunWithNothingFailingIsLogged() {
        when(updates.runPending()).thenReturn(new UpdateManager.Report(List.of(), Optional.empty(), ""));

        new UpdateCommand(new DefaultApplicationArguments("--update"), updates).run();

        verify(updates).runPending();
    }
}
