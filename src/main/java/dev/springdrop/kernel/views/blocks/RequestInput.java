package dev.springdrop.kernel.views.blocks;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The query values of the request being answered, the first of each name, or none outside a request. */
@Component
public class RequestInput {

    public Map<String, String> current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Map<String, String> input = new LinkedHashMap<>();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            request.getParameterMap().forEach((name, values) -> input.put(name, values[0]));
        }
        return input;
    }
}
