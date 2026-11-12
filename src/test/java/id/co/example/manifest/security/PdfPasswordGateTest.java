package id.co.example.manifest.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PdfPasswordGateTest {
    private static final byte[] CUT_OFF = PdfPasswordGate.digest("20261029");

    @Test
    void acceptsOnlyTheCutOffIdOfThatPdf() {
        PdfPasswordGate gate = new PdfPasswordGate();
        assertEquals(PdfPasswordGate.CheckResult.ALLOWED, gate.check("tokenA", "20261029", CUT_OFF));
        assertEquals(PdfPasswordGate.CheckResult.WRONG, gate.check("tokenB", "29102026", CUT_OFF));
        assertEquals(PdfPasswordGate.CheckResult.WRONG, gate.check("tokenC", null, CUT_OFF));
    }

    @Test
    void fiveIncorrectAttemptsLockTokenForFifteenMinutes() {
        Instant start = Instant.parse("2026-10-23T08:00:00Z");
        PdfPasswordGate gate = new PdfPasswordGate(Clock.fixed(start, ZoneOffset.UTC));
        for (int i = 0; i < 4; i++) {
            assertEquals(PdfPasswordGate.CheckResult.WRONG, gate.check("token", "20260101", CUT_OFF));
        }
        assertEquals(PdfPasswordGate.CheckResult.LOCKED, gate.check("token", "20260101", CUT_OFF));
        assertEquals(PdfPasswordGate.CheckResult.LOCKED, gate.check("token", "20261029", CUT_OFF));

        PdfPasswordGate later = new PdfPasswordGate(Clock.fixed(start.plusSeconds(15 * 60), ZoneOffset.UTC));
        assertEquals(PdfPasswordGate.CheckResult.ALLOWED, later.check("token", "20261029", CUT_OFF));
    }
}
