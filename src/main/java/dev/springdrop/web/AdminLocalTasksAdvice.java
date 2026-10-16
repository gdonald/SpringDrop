package dev.springdrop.web;

import dev.springdrop.kernel.theme.Link;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives every view the local tasks of the page it draws, as the {@code localTasks} model attribute. */
@ControllerAdvice
public class AdminLocalTasksAdvice {

    @ModelAttribute("localTasks")
    public List<Link> localTasks(HttpServletRequest request) {
        return AdminLocalTasks.forPath(request.getRequestURI());
    }
}
