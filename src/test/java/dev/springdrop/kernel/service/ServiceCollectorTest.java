package dev.springdrop.kernel.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

class ServiceCollectorTest {

    interface Greeter {
        String greet();
    }

    @Test
    void collectsImplementationsInDeclaredOrder() {
        new ApplicationContextRunner()
                .withUserConfiguration(Greeters.class)
                .run(context -> {
                    ServiceCollector collector = context.getBean(ServiceCollector.class);

                    assertThat(collector.collect(Greeter.class))
                            .extracting(Greeter::greet)
                            .containsExactly("first", "second");
                });
    }

    @Configuration
    static class Greeters {

        @Bean
        ServiceCollector serviceCollector(
                org.springframework.beans.factory.ListableBeanFactory beanFactory) {
            return new ServiceCollector(beanFactory);
        }

        @Bean
        @Order(1)
        Greeter firstGreeter() {
            return () -> "first";
        }

        @Bean
        @Order(2)
        Greeter secondGreeter() {
            return () -> "second";
        }
    }
}
