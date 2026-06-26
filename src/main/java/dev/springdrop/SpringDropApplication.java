package dev.springdrop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringDropApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringDropApplication.class, args);
    }
}
