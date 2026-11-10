package id.co.example.manifest.security.basic;

import id.co.example.manifest.security.config.BasicAuthProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Accounts from {@code asdp.security.basic-auth.users}, whose values come from environment variables.
 * Replace with an ASDP adapter once id.asdp.standardlib:authentication is available (see {@link UserAccountProvider}).
 */
@Component
public class ConfiguredUserAccountProvider implements UserAccountProvider {
    private final Map<String, UserAccount> accounts;

    /**
     * Indexes the configured accounts by username.
     *
     * @param properties Basic Auth configuration
     * @throws IllegalArgumentException when two accounts share a username
     */
    public ConfiguredUserAccountProvider(BasicAuthProperties properties) {
        this.accounts = properties.users().stream()
                .map(u -> new UserAccount(u.username(), u.passwordHash(), u.enabled(), new HashSet<>(u.authorities())))
                .collect(Collectors.toUnmodifiableMap(UserAccount::username, Function.identity(), (a, b) -> {
                    throw new IllegalArgumentException("Username Basic Auth duplikat: " + a.username());
                }));
    }

    /** {@inheritDoc} */
    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(accounts.get(username));
    }
}
