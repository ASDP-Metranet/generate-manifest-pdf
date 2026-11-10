package id.co.example.manifest.security.basic;

import java.util.Set;

/**
 * Stored account used to verify credentials. Holds a password hash only, never a raw password,
 * and is never returned by any API.
 *
 * @param username     login name
 * @param passwordHash hash checked with the configured PasswordEncoder
 * @param enabled      whether the account may authenticate
 * @param authorities  permissions granted to the account
 */
public record UserAccount(String username, String passwordHash, boolean enabled, Set<String> authorities) {
    /**
     * Creates an immutable account.
     *
     * @param username     login name
     * @param passwordHash hash checked with the configured PasswordEncoder
     * @param enabled      whether the account may authenticate
     * @param authorities  permissions granted to the account
     */
    public UserAccount {
        authorities = Set.copyOf(authorities);
    }

    /**
     * Hides the password hash so the account can be logged safely.
     *
     * @return a description without the hash
     */
    @Override
    public String toString() {
        return "UserAccount[username=" + username + ", enabled=" + enabled + ", authorities=" + authorities + "]";
    }
}
