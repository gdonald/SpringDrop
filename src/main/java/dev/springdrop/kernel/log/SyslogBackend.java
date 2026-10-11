package dev.springdrop.kernel.log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The activity log backend that sends each event to a syslog server over UDP,
 * as an RFC 5424 message from the {@code local0} facility. The server is
 * {@code springdrop.logging.syslog.host} and {@code .port}, the local one on
 * port 514 unless they say otherwise.
 */
@Component
public class SyslogBackend implements LogBackend {

    public static final String ID = "syslog";

    public static final String APP_NAME = "springdrop";

    private static final int LOCAL0 = 16;

    private final InetSocketAddress server;

    public SyslogBackend(@Value("${springdrop.logging.syslog.host:localhost}") String host,
            @Value("${springdrop.logging.syslog.port:514}") int port) {
        this.server = new InetSocketAddress(host, port);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void write(LogEntry entry) {
        byte[] message = format(entry).getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(message, message.length, server));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * The RFC 5424 message: priority, version, timestamp, no hostname (the
     * syslog server records the sender), the app name, no process id, the
     * event type as the message id, no structured data, then the text.
     */
    static String format(LogEntry entry) {
        int priority = LOCAL0 * 8 + entry.severity().level();
        return "<" + priority + ">1 " + DateTimeFormatter.ISO_INSTANT.format(entry.timestamp()) + " - " + APP_NAME
                + " - " + entry.type() + " - " + entry.formatted() + "|uid=" + entry.uid() + "|location="
                + entry.location() + "|client=" + entry.hostname();
    }
}
