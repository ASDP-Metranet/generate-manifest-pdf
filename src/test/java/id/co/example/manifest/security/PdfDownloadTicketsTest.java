package id.co.example.manifest.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PdfDownloadTicketsTest {
    private static final Instant START = Instant.parse("2026-11-12T08:00:00Z");

    @Test
    void ticketOpensOnlyItsOwnPdfAndCanBeReusedForDownload() {
        PdfDownloadTickets tickets = new PdfDownloadTickets(Clock.fixed(START, ZoneOffset.UTC));
        String ticket = tickets.issue("fileA");
        assertTrue(tickets.isValid("fileA", ticket));
        assertTrue(tickets.isValid("fileA", ticket), "the download button fetches the same URL again");
        assertFalse(tickets.isValid("fileB", ticket));
        assertFalse(tickets.isValid("fileA", "made-up"));
        assertFalse(tickets.isValid("fileA", null));
        assertNotEquals(ticket, tickets.issue("fileA"));
    }

    @Test
    void ticketExpiresAfterTenMinutes() {
        MutableClock clock = new MutableClock(START);
        PdfDownloadTickets tickets = new PdfDownloadTickets(clock);
        String ticket = tickets.issue("fileA");
        clock.now = START.plusSeconds(9 * 60 + 59);
        assertTrue(tickets.isValid("fileA", ticket));
        clock.now = START.plusSeconds(10 * 60);
        assertFalse(tickets.isValid("fileA", ticket));
    }

    private static final class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
