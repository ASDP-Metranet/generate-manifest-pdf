package id.co.example.manifest.security.basic;

import java.util.Set;

/**
 * Result of a successful authentication. Carries no password or password hash.
 *
 * @param username    authenticated login name
 * @param authorities permissions granted to the user
 */
public record AuthenticatedUser(String username, Set<String> authorities) {
    /**
     * Creates an immutable authenticated user.
     *
     * @param username    authenticated login name
     * @param authorities permissions granted to the user
     */
    public AuthenticatedUser {
        authorities = Set.copyOf(authorities);
    }
}
