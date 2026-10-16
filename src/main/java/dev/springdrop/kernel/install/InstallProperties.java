package dev.springdrop.kernel.install;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Whether the site counts as installed whatever its database says, for a
 * deployment or test suite that sets its site up another way.
 */
@ConfigurationProperties("springdrop.install")
public record InstallProperties(@DefaultValue("false") boolean assumeInstalled) {
}
