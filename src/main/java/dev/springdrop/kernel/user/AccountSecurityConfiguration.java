package dev.springdrop.kernel.user;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * How passwords are hashed. BCrypt is deliberate: it is slow by design, so a
 * stolen table of hashes is expensive to work through.
 */
@Configuration
public class AccountSecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
