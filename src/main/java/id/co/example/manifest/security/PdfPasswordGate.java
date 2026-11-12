package id.co.example.manifest.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checks the password of a stored PDF (its cutOffId written DDMMYYYY) against the digest saved with it,
 * and locks a token for 15 minutes after 5 wrong attempts.
 */
@Service
public class PdfPasswordGate {
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Autowired
    public PdfPasswordGate() {
        this(Clock.systemUTC());
    }

    // package-private constructor for deterministic unit tests
    PdfPasswordGate(Clock clock) {
        this.clock = clock;
    }

    /** SHA-256 of the password: what is stored next to the PDF instead of the password itself. */
    public static byte[] digest(String password) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest((password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 tidak tersedia", ex);
        }
    }

    public CheckResult check(String token, String providedPassword, byte[] expectedDigest) {
        Instant now = clock.instant();
        Attempts state = attempts.computeIfAbsent(token, unused -> new Attempts());
        synchronized (state) {
            if (state.lockedUntil != null && now.isBefore(state.lockedUntil)) {
                return CheckResult.LOCKED;
            }
            if (state.lockedUntil != null && !now.isBefore(state.lockedUntil)) {
                state.failures = 0;
                state.lockedUntil = null;
            }
            if (MessageDigest.isEqual(expectedDigest, digest(providedPassword))) {
                attempts.remove(token, state);
                return CheckResult.ALLOWED;
            }
            state.failures++;
            if (state.failures >= MAX_FAILURES) {
                state.lockedUntil = now.plus(LOCK_DURATION);
                return CheckResult.LOCKED;
            }
            return CheckResult.WRONG;
        }
    }

    public enum CheckResult { ALLOWED, WRONG, LOCKED }
    private static final class Attempts {
        int failures;
        Instant lockedUntil;
    }
}
