package dev.springdrop.kernel.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogEntryTest {

    private static LogEntry entry(String message, Map<String, String> variables) {
        return new LogEntry(null, "action", Severity.WARNING, message, variables, 4L, "http://localhost/admin",
                "", "10.0.0.9", Instant.parse("2026-05-04T10:15:00Z"));
    }

    @Test
    void placeholdersAreFilledAndOneWithoutAVariableIsLeft() {
        assertThat(entry("Moved {title} to {place} for $1.", Map.of("title", "News"))
                .formatted()).isEqualTo("Moved News to {place} for $1.");
    }

    @Test
    void aSeverityIsFoundByItsLevel() {
        assertThat(Severity.ofLevel(3)).contains(Severity.ERROR);
        assertThat(Severity.ofLevel(8)).isEmpty();
        assertThat(Severity.EMERGENCY.label()).isEqualTo("Emergency");
    }

    @Test
    void theSyslogMessageFollowsRfc5424FromLocal0() {
        assertThat(SyslogBackend.format(entry("Blocked {name}.", Map.of("name", "ada"))))
                .isEqualTo("<132>1 2026-05-04T10:15:00Z - springdrop - action - Blocked ada.|uid=4"
                        + "|location=http://localhost/admin|client=10.0.0.9");
    }

    @Test
    void theSyslogBackendSendsTheMessageToTheServer() throws Exception {
        try (DatagramSocket server = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(10_000);
            SyslogBackend backend = new SyslogBackend("127.0.0.1", server.getLocalPort());

            backend.write(entry("Sent.", Map.of()));

            DatagramPacket received = new DatagramPacket(new byte[2048], 2048);
            server.receive(received);
            assertThat(new String(received.getData(), 0, received.getLength(), StandardCharsets.UTF_8))
                    .contains(" - action - Sent.|uid=4");
            assertThat(backend.id()).isEqualTo(SyslogBackend.ID);
        }
    }

    @Test
    void aMessageTooLongForADatagramFailsTheWrite() {
        SyslogBackend backend = new SyslogBackend("127.0.0.1", 9);

        assertThatThrownBy(() -> backend.write(entry("x".repeat(70_000), Map.of())))
                .isInstanceOf(UncheckedIOException.class);
    }
}
