package id.co.example.manifest.security.basic;

import java.util.Optional;

/**
 * Source of accounts for Basic Authentication.
 *
 * <p>ASDP integration point: the ASDP standard library (id.asdp.standardlib:authentication) was not
 * available to this build, so accounts currently come from configuration. When the library is
 * available, add an adapter implementing this interface that maps its user model (for example
 * User, UserAuth or UserConfig) to {@link UserAccount}.
 */
public interface UserAccountProvider {
    /**
     * Looks up an account by its exact username.
     *
     * @param username login name sent by the client
     * @return the account, or empty when no such account exists
     */
    Optional<UserAccount> findByUsername(String username);
}
