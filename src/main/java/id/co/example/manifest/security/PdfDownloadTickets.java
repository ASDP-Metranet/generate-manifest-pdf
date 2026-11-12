package id.co.example.manifest.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived tickets issued after a correct PDF password. The browser then loads the PDF with a plain
 * GET, which its download button can repeat; a POST response cannot be fetched again without the password.
 * Tickets live in memory only, so a restart simply asks for the password again.
 */
@Component
public class PdfDownloadTickets {
    private static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final SecureRandom RNG = new SecureRandom();
    private final Clock clock;
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    @Autowired
    public PdfDownloadTickets() {
        this(Clock.systemUTC());
    }

    // package-private constructor for deterministic unit tests
    PdfDownloadTickets(Clock clock) {
        this.clock = clock;
    }

    /** Issues a new ticket for one stored PDF, valid for 10 minutes and reusable within that time. */
    public String issue(String fileToken) {
        Instant now = clock.instant();
        tickets.values().removeIf(t -> !now.isBefore(t.expiresAt()));
        byte[] entropy = new byte[32];
        RNG.nextBytes(entropy);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        tickets.put(ticket, new Ticket(fileToken, now.plus(VALIDITY)));
        return ticket;
    }

    /** True when the ticket was issued for this PDF and has not expired. */
    public boolean isValid(String fileToken, String ticket) {
        if (ticket == null) {
            return false;
        }
        Ticket found = tickets.get(ticket);
        return found != null && found.fileToken().equals(fileToken) && clock.instant().isBefore(found.expiresAt());
    }

    private record Ticket(String fileToken, Instant expiresAt) {}
}
