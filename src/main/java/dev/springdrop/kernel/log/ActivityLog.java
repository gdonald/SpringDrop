package dev.springdrop.kernel.log;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.user.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The site's activity log. An event is logged on a channel naming its type,
 * stamped with the signed-in account and, during a request, the address asked
 * for, the referring page, and the client address, then written to each
 * backend the logging settings name. A backend that fails to write is reported
 * to the application log and the others still get the event.
 */
@Component
public class ActivityLog {

    private static final Logger LOG = LoggerFactory.getLogger(ActivityLog.class);

    private final ConfigStore config;
    private final List<LogBackend> backends;
    private final Clock clock;

    public ActivityLog(ConfigStore config, List<LogBackend> backends, Clock clock) {
        this.config = config;
        this.backends = backends;
        this.clock = clock;
    }

    public LogChannel channel(String type) {
        return new LogChannel(type, this);
    }

    public LoggingSettings settings() {
        return config.read(LoggingSettings.CONFIG_NAME, LoggingSettings.class, LoggingSettings.DEFAULTS);
    }

    public void save(LoggingSettings settings) {
        config.save(LoggingSettings.CONFIG_NAME, settings);
    }

    /** The ids of the backends events can be written to. */
    public List<String> backendIds() {
        return backends.stream().map(LogBackend::id).sorted().toList();
    }

    public void log(String type, Severity severity, String message, Map<String, String> variables) {
        HttpServletRequest request = currentRequest();
        LogEntry entry = new LogEntry(null, type, severity, message, variables, currentAccount(),
                request == null ? "" : request.getRequestURL().toString(),
                request == null ? "" : Objects.requireNonNullElse(request.getHeader("Referer"), ""),
                request == null ? "" : request.getRemoteAddr(),
                clock.instant());
        List<String> enabled = settings().backends();
        backends.stream().filter(backend -> enabled.contains(backend.id())).forEach(backend -> write(backend, entry));
    }

    private static void write(LogBackend backend, LogEntry entry) {
        try {
            backend.write(entry);
        } catch (RuntimeException failure) {
            LOG.error("Log backend {} could not write: {}", backend.id(), entry.formatted(), failure);
        }
    }

    private static long currentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account)
                ? account.id() : 0L;
    }

    private static HttpServletRequest currentRequest() {
        return (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
                ? attributes.getRequest() : null;
    }
}
