package id.co.example.manifest.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LocalPdfStorageTest {
    @TempDir Path root;

    @Test
    void writesPdfWithSecureTokenAndReadsItBack() throws Exception {
        LocalPdfStorage store = new LocalPdfStorage(root.toString());
        store.initialize();
        byte[] data = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        String token = store.save(data);
        assertEquals(43, token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertTrue(store.exists(token));
        assertArrayEquals(data, store.read(token));
        assertTrue(java.nio.file.Files.exists(root.resolve(token + ".pdf")));
        assertFalse(store.exists("../../etc/passwd"));
    }

    @Test
    void generatesDifferentTokensForDifferentFiles() throws Exception {
        LocalPdfStorage store = new LocalPdfStorage(root.toString());
        store.initialize();
        byte[] data = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);
        assertNotEquals(store.save(data), store.save(data));
    }
}
