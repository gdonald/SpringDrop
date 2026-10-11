package dev.springdrop.kernel.cron;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Starts a cron run once a request has been answered, when automated cron is
 * due. The run goes to the task executor so the visitor whose request started
 * it does not wait for it. The filter sits outside the page cache so a page
 * answered from the cache still counts.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class AutomatedCronFilter extends OncePerRequestFilter {

    private final AutomatedCron automatedCron;
    private final CronRunner cron;
    private final Executor executor;

    public AutomatedCronFilter(AutomatedCron automatedCron, CronRunner cron,
            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME) Executor executor) {
        this.automatedCron = automatedCron;
        this.cron = cron;
        this.executor = executor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (automatedCron.isDue()) {
            executor.execute(cron::run);
        }
    }
}
