package id.co.example.manifest.storage;

import id.co.example.manifest.security.PdfPasswordGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalPdfStorageTest {
    private static final byte[] PASSWORD = PdfPasswordGate.digest("20261029");
    @TempDir Path root;

    @Test
    void writesPdfWithSecureTokenAndReadsItBack() throws Exception {
        LocalPdfStorage store = new LocalPdfStorage(root.toString());
        store.initialize();
        byte[] data = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        String token = store.save(data, PASSWORD);
        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(store.exists(token));
        assertArrayEquals(data, store.read(token));
        assertTrue(Files.exists(root.resolve(token + ".pdf")));
        assertFalse(store.exists("../../etc/passwd"));
    }

    @Test
    void storesPasswordDigestNotThePassword() throws Exception {
        LocalPdfStorage store = new LocalPdfStorage(root.toString());
        store.initialize();
        String token = store.save("%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII), PASSWORD);
        assertArrayEquals(PASSWORD, store.readPasswordDigest(token));
        assertFalse(Files.readString(root.resolve(token + ".key")).contains("20261029"));
    }

    @Test
    void pdfWithoutPasswordKeyIsNotServed() throws Exception {
        LocalPdfStorage store = new LocalPdfStorage(root.toString());
        store.initialize();
        String token = store.save("%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII), PASSWORD);
        Files.delete(root.resolve(token + ".key"));
        assertFalse(store.exists(token));
    }

    @Test
    void generatesDifferentTokensForDifferentFiles() throws Exception {
        LocalPdfStorage store = new LocalPdfStorage(root.toString());
        store.initialize();
        byte[] data = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        assertNotEquals(store.save(data, PASSWORD), store.save(data, PASSWORD));
    }
}
