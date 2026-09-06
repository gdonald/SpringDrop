package dev.springdrop.kernel.token;

/**
 * One token a provider offers, as shown in the token browser: the name that
 * follows the type in {@code [type:name]}, and what it resolves to.
 */
public record TokenDefinition(String name, String description) {
}
