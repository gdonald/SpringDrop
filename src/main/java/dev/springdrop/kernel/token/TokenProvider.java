package dev.springdrop.kernel.token;

import java.util.List;

/**
 * Resolves tokens of one type (the part before the colon in {@code [type:name]}).
 * A provider returns the replacement for a token name, or {@code null} when it
 * does not recognize it. A provider may parse a multi-part name itself to support
 * chained tokens like {@code [node:author:name]}, and lists the tokens it offers
 * so the token browser can show them.
 */
public interface TokenProvider {

    String type();

    String resolve(String name, TokenContext context);

    List<TokenDefinition> availableTokens();
}
