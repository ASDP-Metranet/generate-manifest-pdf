package id.co.example.manifest.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** Shared configuration and HTTP helpers for the Basic Auth integration tests. */
public final class BasicAuthTestSupport {
    public static final String ADMIN = "manifest-admin";
    public static final String CHECK_ONLY = "check-only";
    public static final String DISABLED = "disabled-user";
    public static final String PASSWORD = "S3cret-Test-Only!";
    // Low cost keeps the tests fast; the hash format is the same as in production.
    private static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    private BasicAuthTestSupport() {
    }

    /** Settings that let the application start without a database or real credentials. */
    public static void register(DynamicPropertyRegistry registry) {
        try {
            Path storage = Files.createTempDirectory("manifest-storage-test");
            registry.add("manifest.storage.path", storage::toString);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        registry.add("spring.datasource.url", () -> "jdbc:mysql://127.0.0.1:1/none");
        registry.add("spring.datasource.username", () -> "test");
        registry.add("spring.datasource.password", () -> "test");
        registry.add("asdp.security.basic-auth.users[0].username", () -> ADMIN);
        registry.add("asdp.security.basic-auth.users[0].password-hash", () -> HASH);
        registry.add("asdp.security.basic-auth.users[0].authorities",
                () -> "MANIFEST_GENERATE,MANIFEST_CHECK,MANIFEST_APPROVAL_RESET");
        registry.add("asdp.security.basic-auth.users[0].enabled", () -> "true");
        registry.add("asdp.security.basic-auth.users[1].username", () -> CHECK_ONLY);
        registry.add("asdp.security.basic-auth.users[1].password-hash", () -> HASH);
        registry.add("asdp.security.basic-auth.users[1].authorities", () -> "MANIFEST_CHECK");
        registry.add("asdp.security.basic-auth.users[2].username", () -> DISABLED);
        registry.add("asdp.security.basic-auth.users[2].password-hash", () -> HASH);
        registry.add("asdp.security.basic-auth.users[2].authorities", () -> "MANIFEST_GENERATE");
        registry.add("asdp.security.basic-auth.users[2].enabled", () -> "false");
    }

    /** Value of an Authorization header for the given credentials. */
    public static String basic(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /** Sends a request and returns the response with a string body. */
    public static HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    /** POST of the example manifest to generate-pdf, optionally with an Authorization header. */
    public static HttpRequest.Builder generatePdf(int port, String authorization) {
        try (var in = BasicAuthTestSupport.class.getResourceAsStream("/manifest-request.json")) {
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            HttpRequest.Builder request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/api/v1/manifests/generate-pdf"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            return authorization == null ? request : request.header("Authorization", authorization);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
