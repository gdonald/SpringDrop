package dev.springdrop.kernel.web;

import dev.springdrop.kernel.log.ActivityLog;
import dev.springdrop.kernel.log.ErrorDisplay;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

/**
 * Draws the page for a server error with as much about the error as the
 * logging settings show: nothing beyond that something went wrong, the
 * error's message, or its message and stack trace. Everyone who meets the
 * error sees the same page.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ErrorDisplayViewResolver implements ErrorViewResolver {

    public static final String MESSAGE = "errorMessage";

    public static final String TRACE = "errorTrace";

    private final ActivityLog log;

    public ErrorDisplayViewResolver(ActivityLog log) {
        this.log = log;
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        if (!status.is5xxServerError()) {
            return null;
        }
        Map<String, Object> shown = new LinkedHashMap<>(model);
        ErrorDisplay display = log.settings().errorDisplay();
        if (display != ErrorDisplay.NONE
                && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) instanceof Throwable error) {
            shown.put(MESSAGE, error.getClass().getSimpleName() + ": " + error.getMessage());
            if (display == ErrorDisplay.ALL) {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                shown.put(TRACE, trace.toString());
            }
        }
        return new ModelAndView("error/500", shown, status);
    }
}
