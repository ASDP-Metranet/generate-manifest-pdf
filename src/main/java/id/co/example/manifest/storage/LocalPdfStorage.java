package id.co.example.manifest.storage;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Saves PDFs only in a private local directory. File names are 256-bit random tokens,
 * never client-provided names or manifest IDs. No HTTP static-resource mapping is used.
 */
@Service
public class LocalPdfStorage {
    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final SecureRandom RNG = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(LocalPdfStorage.class);
    private final Path directory;
    private final AtomicBoolean permissionWarningLogged = new AtomicBoolean();

    public LocalPdfStorage(@Value("${manifest.storage.path:./storage}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Path storage bukan direktori: " + directory);
        }
        restrictPermissions(directory, true);
    }

    /**
     * Saves the PDF with the digest of its password in {@code <token>.key}. The key is written first,
     * so a PDF never becomes reachable without its password.
     */
    public String save(byte[] pdfBytes, byte[] passwordDigest) throws IOException {
        if (pdfBytes == null || pdfBytes.length < 5) {
            throw new IllegalArgumentException("Konten PDF kosong");
        }
        if (passwordDigest == null || passwordDigest.length == 0) {
            throw new IllegalArgumentException("Password PDF kosong");
        }
        byte[] entropy = new byte[32];
        RNG.nextBytes(entropy);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
        writeAtomically(directory.resolve(token + ".key"), HexFormat.of().formatHex(passwordDigest)
                .getBytes(StandardCharsets.US_ASCII));
        writeAtomically(directory.resolve(token + ".pdf"), pdfBytes);
        return token;
    }

    /** A document exists only with both its PDF and its password key; PDFs saved before cutOffId have no key. */
    public boolean exists(String token) {
        return SAFE_TOKEN.matcher(token).matches()
                && Files.isRegularFile(directory.resolve(token + ".pdf"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(directory.resolve(token + ".key"), LinkOption.NOFOLLOW_LINKS);
    }

    public byte[] readPasswordDigest(String token) throws IOException {
        if (!exists(token)) {
            throw new java.nio.file.NoSuchFileException("File tidak ditemukan");
        }
        String hex = Files.readString(directory.resolve(token + ".key"), StandardCharsets.US_ASCII).trim();
        return HexFormat.of().parseHex(hex);
    }

    private void writeAtomically(Path destination, byte[] content) throws IOException {
        Path temporary = Files.createTempFile(directory, ".upload-", ".tmp");
        try {
            Files.write(temporary, content);
            restrictPermissions(temporary, false);
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public byte[] read(String token) throws IOException {
        if (!exists(token)) {
            throw new java.nio.file.NoSuchFileException("File tidak ditemukan");
        }
        return Files.readAllBytes(directory.resolve(token + ".pdf"));
    }

    private void restrictPermissions(Path path, boolean isDirectory) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(
                    isDirectory ? "rwx------" : "rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows & non-POSIX filesystems: permissions are delegated to volume ACLs.
        } catch (IOException ex) {
            // A Windows folder bind-mounted into Docker rejects chmod; access then depends on the
            // host folder. Warn once instead of failing startup or every saved PDF.
            if (permissionWarningLogged.compareAndSet(false, true)) {
                log.warn("Tidak dapat membatasi permission storage {} ({}). Pastikan folder storage di host "
                        + "tidak dapat dibaca user lain.", directory, ex.getMessage());
            }
        }
    }
}
