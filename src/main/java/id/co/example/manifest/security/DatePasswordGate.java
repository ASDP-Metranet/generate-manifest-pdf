package id.co.example.manifest.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

/** Access date (not document date) in configured timezone, default Asia/Jakarta. */
@Service
public class DatePasswordGate {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("ddMMuuuu");
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private final Clock clock;
    private final ConcurrentHashMap<String, Attempts> attempts = new ConcurrentHashMap<>();
    
    @Autowired
    public DatePasswordGate(@Value("${manifest.access.zone:Asia/Jakarta}") String timezone) {
        this(Clock.system(ZoneId.of(timezone)));
    }

    // package-private constructor for deterministic unit tests
    DatePasswordGate(Clock clock) {
        this.clock = clock;
    }

    public String todayPassword() {
        return LocalDate.now(clock).format(FORMAT);
    }

    public CheckResult check(String token, String providedPassword) {
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
            byte[] actual = (providedPassword == null ? "" : providedPassword).getBytes(StandardCharsets.UTF_8);
            byte[] expected = todayPassword().getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, actual)) {
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
