package dev.springdrop.kernel;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared kernel beans. The {@link Clock} is injected wherever time is read so it
 * can be controlled in tests rather than calling the system clock directly.
 */
@Configuration
public class KernelConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
