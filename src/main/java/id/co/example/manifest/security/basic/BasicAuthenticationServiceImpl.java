package id.co.example.manifest.security.basic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Verifies Basic Auth credentials against a {@link UserAccountProvider} with a {@link PasswordEncoder}.
 * Logs authentication events without any credential.
 */
@Service
public class BasicAuthenticationServiceImpl implements AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(BasicAuthenticationServiceImpl.class);

    private final UserAccountProvider accounts;
    private final PasswordEncoder passwordEncoder;
    // Unknown usernames are checked against this hash so they take as long as a wrong password.
    private final String unknownUserHash;

    /**
     * Creates the service.
     *
     * @param accounts        source of accounts
     * @param passwordEncoder encoder used to verify password hashes
     */
    public BasicAuthenticationServiceImpl(UserAccountProvider accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** {@inheritDoc} */
    @Override
    public AuthenticatedUser authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null) {
            log.warn("Authentication failure reason=missing_credentials");
            throw new InvalidCredentialException();
        }
        Optional<UserAccount> account = accounts.findByUsername(username);
        boolean passwordMatches = passwordEncoder.matches(password,
                account.map(UserAccount::passwordHash).orElse(unknownUserHash));
        if (account.isEmpty()) {
            // The unknown name is not logged: it may be a password typed into the wrong field.
            log.warn("Authentication failure reason=unknown_user");
            throw new InvalidCredentialException();
        }
        UserAccount user = account.get();
        if (!passwordMatches) {
            log.warn("Authentication failure reason=wrong_password user={}", user.username());
            throw new InvalidCredentialException();
        }
        if (!user.enabled()) {
            log.warn("Authentication rejected reason=user_disabled user={}", user.username());
            throw new UserDisabledException();
        }
        log.info("Authentication success user={}", user.username());
        return new AuthenticatedUser(user.username(), user.authorities());
    }
}
