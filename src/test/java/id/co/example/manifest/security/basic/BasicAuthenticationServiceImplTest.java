package id.co.example.manifest.security.basic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicAuthenticationServiceImplTest {
    private static final String HASH = "$2a$12$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz01234";
    private static final String UNKNOWN_USER_HASH = "$2a$12$unknownunknownunknownuuabcdefghijklmnopqrstuvwxyz01234";

    @Mock UserAccountProvider accounts;
    @Mock PasswordEncoder passwordEncoder;
    BasicAuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(UNKNOWN_USER_HASH);
        service = new BasicAuthenticationServiceImpl(accounts, passwordEncoder);
    }

    @Test
    void correctPasswordIsVerifiedWithPasswordEncoder() {
        when(accounts.findByUsername("api-user")).thenReturn(Optional.of(account(true)));
        when(passwordEncoder.matches("secret", HASH)).thenReturn(true);

        AuthenticatedUser user = service.authenticate("api-user", "secret");

        verify(passwordEncoder).matches("secret", HASH);
        assertEquals("api-user", user.username());
        assertEquals(Set.of("MANIFEST_CHECK"), user.authorities());
    }

    @Test
    void wrongPasswordIsRejected() {
        when(accounts.findByUsername("api-user")).thenReturn(Optional.of(account(true)));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        assertThrows(InvalidCredentialException.class, () -> service.authenticate("api-user", "wrong"));
    }

    @Test
    void unknownUserIsRejectedAfterTheSamePasswordCheck() {
        when(accounts.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialException.class, () -> service.authenticate("ghost", "secret"));
        // A password check still runs, so unknown and known usernames take about the same time.
        verify(passwordEncoder).matches("secret", UNKNOWN_USER_HASH);
    }

    @Test
    void disabledUserWithCorrectPasswordIsRejected() {
        when(accounts.findByUsername("api-user")).thenReturn(Optional.of(account(false)));
        when(passwordEncoder.matches("secret", HASH)).thenReturn(true);

        assertThrows(UserDisabledException.class, () -> service.authenticate("api-user", "secret"));
    }

    @Test
    void disabledUserWithWrongPasswordLooksLikeAnyWrongPassword() {
        when(accounts.findByUsername("api-user")).thenReturn(Optional.of(account(false)));
        when(passwordEncoder.matches("wrong", HASH)).thenReturn(false);

        assertThrows(InvalidCredentialException.class, () -> service.authenticate("api-user", "wrong"));
    }

    @Test
    void missingCredentialsAreRejectedWithoutLookup() {
        assertThrows(InvalidCredentialException.class, () -> service.authenticate("", "secret"));
        assertThrows(InvalidCredentialException.class, () -> service.authenticate("api-user", null));
        verify(accounts, never()).findByUsername(any());
        verify(passwordEncoder, never()).matches(any(), eq(HASH));
    }

    private static UserAccount account(boolean enabled) {
        return new UserAccount("api-user", HASH, enabled, Set.of("MANIFEST_CHECK"));
    }
}
