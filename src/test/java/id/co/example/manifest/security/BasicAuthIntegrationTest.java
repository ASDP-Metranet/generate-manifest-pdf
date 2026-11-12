package id.co.example.manifest.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import id.co.example.manifest.repository.DockShip;
import id.co.example.manifest.repository.DockShipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static id.co.example.manifest.security.BasicAuthTestSupport.ADMIN;
import static id.co.example.manifest.security.BasicAuthTestSupport.CHECK_ONLY;
import static id.co.example.manifest.security.BasicAuthTestSupport.DISABLED;
import static id.co.example.manifest.security.BasicAuthTestSupport.PASSWORD;
import static id.co.example.manifest.security.BasicAuthTestSupport.basic;
import static id.co.example.manifest.security.BasicAuthTestSupport.generatePdf;
import static id.co.example.manifest.security.BasicAuthTestSupport.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** End-to-end Basic Auth checks through the embedded server and the full Spring Security chain. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BasicAuthIntegrationTest {
    private static final String CHECK_PATH =
            "/checkmanifets?id=ABC&branch_id=1011&harbour_id=348&route_id=5510";

    @Value("${local.server.port}") int port;
    @MockitoBean DockShipRepository dockShips;

    private final List<HttpResponse<String>> responses = new ArrayList<>();
    private ListAppender<ILoggingEvent> logs;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        BasicAuthTestSupport.register(registry);
    }

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        rootLogger().addAppender(logs);
        when(dockShips.findByIdAndBranchIdAndHarborIdAndRouteId(any(), any(), any(), any()))
                .thenReturn(Optional.of(new DockShip("ABC", 1011L, 348L, 5510L)));
    }

    @AfterEach
    void noCredentialInResponsesOrLogs() {
        rootLogger().detachAppender(logs);
        List<String> secrets = List.of(PASSWORD, basic(ADMIN, PASSWORD), basic(ADMIN, PASSWORD).substring(6),
                basic(CHECK_ONLY, PASSWORD).substring(6), basic(DISABLED, PASSWORD).substring(6));
        for (ILoggingEvent event : logs.list) {
            String line = event.getFormattedMessage() + event.getMDCPropertyMap();
            secrets.forEach(secret -> assertFalse(line.contains(secret), "credential in log: " + line));
        }
        for (HttpResponse<String> response : responses) {
            secrets.forEach(secret -> assertFalse(response.body().contains(secret), "credential in response"));
            assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "stateless: no cookie/session");
        }
    }

    @Test
    void missingAuthorizationReturns401WithStandardBody() {
        HttpResponse<String> response = call(generatePdf(port, null).header("X-Correlation-Id", "test-corr-1"));

        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("")
                .startsWith("Basic realm=\"asdp-internal-api\""));
        assertTrue(response.body().contains("\"code\":\"AUTH-401\""));
        assertTrue(response.body().contains("\"message\":\"Kredensial tidak valid\""));
        assertTrue(response.body().contains("\"path\":\"/api/v1/manifests/generate-pdf\""));
        assertTrue(response.body().contains("\"correlationId\":\"test-corr-1\""));
        assertTrue(response.body().matches(".*\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T.*"));
    }

    @Test
    void wrongPasswordUnknownUserAndMalformedHeaderAllReturnTheSame401() {
        for (String authorization : List.of(basic(ADMIN, "wrong"), basic("ghost", PASSWORD), "Basic !!not-base64!!")) {
            HttpResponse<String> response = call(generatePdf(port, authorization));
            assertEquals(401, response.statusCode(), authorization);
            assertTrue(response.body().contains("\"code\":\"AUTH-401\""));
        }
        // Each failed login is verified exactly once (no second BCrypt check by a parent manager).
        assertEquals(1, logs.list.stream()
                .filter(e -> e.getFormattedMessage().startsWith("Authentication failure reason=wrong_password")).count());
    }

    @Test
    void disabledUserIsRejected() {
        HttpResponse<String> response = call(generatePdf(port, basic(DISABLED, PASSWORD)));

        assertEquals(401, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"AUTH-USER-DISABLED\""));
    }

    @Test
    void correctCredentialsReachProtectedEndpoints() {
        assertEquals(201, call(generatePdf(port, basic(ADMIN, PASSWORD))).statusCode());

        HttpResponse<String> check = call(get(CHECK_PATH).header("Authorization", basic(ADMIN, PASSWORD)));
        assertEquals(200, check.statusCode());
        assertTrue(check.body().contains("\"found\":true"));
        assertTrue(logs.list.stream().anyMatch(e -> e.getFormattedMessage().equals("Authentication success user=" + ADMIN)));
    }

    @Test
    void userWithoutRequiredAuthorityGets403() {
        HttpResponse<String> generate = call(generatePdf(port, basic(CHECK_ONLY, PASSWORD)));
        assertEquals(403, generate.statusCode());
        assertTrue(generate.body().contains("\"code\":\"AUTH-403\""));

        HttpResponse<String> reset = call(HttpRequest.newBuilder(uri("/manitfestnotapprove?id=ABC"))
                .header("Authorization", basic(CHECK_ONLY, PASSWORD))
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertEquals(403, reset.statusCode());

        assertEquals(200, call(get(CHECK_PATH).header("Authorization", basic(CHECK_ONLY, PASSWORD))).statusCode());
    }

    @Test
    void publicPathsNeedNoCredentials() {
        HttpResponse<String> generated = call(generatePdf(port, basic(ADMIN, PASSWORD)));
        Matcher fileId = Pattern.compile("\"fileId\":\"([^\"]+)\"").matcher(generated.body());
        assertTrue(fileId.find());

        HttpResponse<String> page = call(get("/api/v1/manifests/files/" + fileId.group(1)));
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("DDMMYYYY</b> (contoh: 25122026)"));
        assertFalse(page.body().contains("WIB"));

        HttpResponse<String> wrong = call(HttpRequest.newBuilder(uri("/api/v1/manifests/files/" + fileId.group(1) + "/unlock"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("password=20261029")));
        assertTrue(wrong.body().contains("Gunakan tanggal cutoff/keberangkatan (DDMMYYYY)."));
        assertEquals(401, wrong.statusCode());
        HttpResponse<String> unlocked = call(HttpRequest.newBuilder(uri("/api/v1/manifests/files/" + fileId.group(1) + "/unlock"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("password=29102026")));
        // Correct password → 303 to a GET URL, so the browser's download button can fetch the PDF again.
        assertEquals(303, unlocked.statusCode());
        String pdfUrl = unlocked.headers().firstValue("Location").orElseThrow();
        assertTrue(pdfUrl.startsWith("/api/v1/manifests/files/" + fileId.group(1) + "/pdf?ticket="));
        for (int fetch = 0; fetch < 2; fetch++) {
            HttpResponse<String> pdf = call(get(pdfUrl));
            assertEquals(200, pdf.statusCode());
            assertTrue(pdf.headers().firstValue("Content-Type").orElse("").startsWith("application/pdf"));
            assertTrue(pdf.body().startsWith("%PDF-"));
        }
        HttpResponse<String> noTicket = call(get("/api/v1/manifests/files/" + fileId.group(1) + "/pdf?ticket=made-up"));
        assertEquals(303, noTicket.statusCode());
        assertEquals("/api/v1/manifests/files/" + fileId.group(1), noTicket.headers().firstValue("Location").orElse(""));
        // A missing document on a public path stays 404 instead of turning into 401.
        assertEquals(404, call(get("/api/v1/manifests/files/does-not-exist")).statusCode());
    }

    @Test
    void unknownPathsRequireAuthentication() {
        assertEquals(401, call(get("/anything-else")).statusCode());
    }

    private HttpResponse<String> call(HttpRequest.Builder request) {
        HttpResponse<String> response = send(request);
        responses.add(response);
        return response;
    }

    private HttpRequest.Builder get(String path) {
        return HttpRequest.newBuilder(uri(path)).GET();
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static Logger rootLogger() {
        return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }
}
