package id.co.example.manifest.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class DatePasswordGateTest {
    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");

    @Test
    void usesCurrentAccessDateInJakarta() {
        // 16:59 UTC = 23:59 WIB 2026-10-23
        Clock beforeMidnight = Clock.fixed(Instant.parse("2026-10-23T16:59:00Z"), WIB);
        DatePasswordGate gate = new DatePasswordGate(beforeMidnight);
        assertEquals("23102026", gate.todayPassword());
        assertEquals(DatePasswordGate.CheckResult.ALLOWED, gate.check("tokenA", "23102026"));
        assertEquals(DatePasswordGate.CheckResult.WRONG, gate.check("tokenB", "22102026"));
        Clock afterMidnight = Clock.fixed(Instant.parse("2026-10-23T17:01:00Z"), WIB);
        assertEquals("24102026", new DatePasswordGate(afterMidnight).todayPassword());
    }

    @Test
    void fiveIncorrectAttemptsLockToken() {
        DatePasswordGate gate = new DatePasswordGate(Clock.fixed(
                Instant.parse("2026-10-23T08:00:00Z"), WIB));
        for (int i = 0; i < 4; i++) {
            assertEquals(DatePasswordGate.CheckResult.WRONG, gate.check("token", "11112026"));
        }
        assertEquals(DatePasswordGate.CheckResult.LOCKED, gate.check("token", "11112026"));
        assertEquals(DatePasswordGate.CheckResult.LOCKED, gate.check("token", "23102026"));
    }
}
