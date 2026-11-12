package id.co.example.manifest.api;

import id.co.example.manifest.dto.ManifestRequest;
import id.co.example.manifest.security.PdfDownloadTickets;
import id.co.example.manifest.security.PdfPasswordGate;
import id.co.example.manifest.service.ManifestPdfService;
import id.co.example.manifest.storage.LocalPdfStorage;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SEE_OTHER;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@RestController
@RequestMapping("/api/v1/manifests")
public class ManifestPdfController {
    private final ManifestPdfService generator;
    private final LocalPdfStorage storage;
    private final PdfPasswordGate passwordGate;
    private final PdfDownloadTickets downloadTickets;
    private final String publicBaseUrl;
    private final ZoneId timezone;

    public ManifestPdfController(ManifestPdfService generator, LocalPdfStorage storage,
                                 PdfPasswordGate passwordGate, PdfDownloadTickets downloadTickets,
                                 @Value("${manifest.public-base-url:http://localhost:8080}") String publicBaseUrl,
                                 @Value("${manifest.access.zone:Asia/Jakarta}") String timezone) {
        this.generator = generator;
        this.storage = storage;
        this.passwordGate = passwordGate;
        this.downloadTickets = downloadTickets;
        this.timezone = ZoneId.of(timezone);
        String configured = publicBaseUrl.replaceAll("/+$", "");
        URI base = URI.create(configured);
        if (base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null
                || !("https".equalsIgnoreCase(base.getScheme())
                || "http".equalsIgnoreCase(base.getScheme()))) {
            throw new IllegalArgumentException("manifest.public-base-url harus URL HTTP(S) valid");
        }
        this.publicBaseUrl = configured;
    }

    /** Unlike the previous release, generation now returns JSON containing a view URL. */
    @PostMapping(value = "/generate-pdf", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GeneratedPdfResponse> generatePdf(@Valid @RequestBody ManifestRequest request) {
        try {
            byte[] pdf = generator.generate(request);
            String token = storage.save(pdf, PdfPasswordGate.digest(pdfPassword(request.document().cutOffId())));
            String url = publicBaseUrl + "/api/v1/manifests/files/" + token;
            return ResponseEntity.status(201)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new GeneratedPdfResponse(token, url,
                            OffsetDateTime.now(timezone).toString(), "DDMMYYYY"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Data manifest tidak valid", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Gagal membuat/menyimpan PDF", ex);
        }
    }

    @GetMapping(value = "/files/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> openPasswordPage(@PathVariable String token) {
        ensureExists(token);
        return htmlResponse(200, passwordPage(token, null));
    }

    /**
     * Browser form POST: when the password (the manifest's cutOffId) is correct, redirects (303) to a GET
     * URL with a short-lived ticket. The PDF is served there, so the browser's download button can fetch it
     * again; a POST response cannot be re-fetched without resending the password.
     */
    @PostMapping(value = "/files/{token}/unlock", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> unlock(@PathVariable String token, @RequestParam("password") String password) {
        ensureExists(token);
        byte[] expected;
        try {
            expected = storage.readPasswordDigest(token);
        } catch (IOException | IllegalArgumentException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Gagal membaca password PDF", ex);
        }
        PdfPasswordGate.CheckResult result = passwordGate.check(token, password, expected);
        if (result == PdfPasswordGate.CheckResult.LOCKED) {
            return htmlResponse(TOO_MANY_REQUESTS.value(),
                    passwordPage(token, "Terlalu banyak percobaan. Coba lagi dalam 15 menit."));
        }
        if (result == PdfPasswordGate.CheckResult.WRONG) {
            return htmlResponse(UNAUTHORIZED.value(),
                    passwordPage(token, "Password salah. Gunakan tanggal cutoff/keberangkatan (DDMMYYYY)."));
        }
        // Relative location: works the same behind a reverse proxy. Token and ticket are URL-safe.
        return ResponseEntity.status(SEE_OTHER)
                .header(HttpHeaders.LOCATION, "/api/v1/manifests/files/" + token + "/pdf?ticket=" + downloadTickets.issue(token))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    /** Serves the PDF inline to a holder of a valid ticket; an expired or wrong ticket goes back to the password page. */
    @GetMapping("/files/{token}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token, @RequestParam(value = "ticket", required = false) String ticket) {
        ensureExists(token);
        if (!downloadTickets.isValid(token, ticket)) {
            return ResponseEntity.status(SEE_OTHER)
                    .header(HttpHeaders.LOCATION, "/api/v1/manifests/files/" + token)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        try {
            byte[] pdf = storage.read(token);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("X-Robots-Tag", "noindex, nofollow")
                    .header("Referrer-Policy", "no-referrer")
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline().filename("manifest-penumpang.pdf").build().toString())
                    .body(pdf);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Gagal membaca file PDF", ex);
        }
    }

    /**
     * The PDF password is the date in the first 8 digits of cutOffId (YYYYMMDD), written day first:
     * 20241023143733008048400 becomes 23102024. Later digits are ignored.
     */
    static String pdfPassword(String cutOffId) {
        return cutOffId.substring(6, 8) + cutOffId.substring(4, 6) + cutOffId.substring(0, 4);
    }

    private void ensureExists(String token) {
        if (!storage.exists(token)) {
            throw new ResponseStatusException(NOT_FOUND, "Dokumen tidak ditemukan");
        }
    }

    private static ResponseEntity<String> htmlResponse(int status, String content) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Robots-Tag", "noindex, nofollow")
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")
                .body(content);
    }

    private static String passwordPage(String token, String error) {
        // token is a validated URL-safe random string from a private file lookup.
        String errorHtml = error == null ? "" : "<p class=\"err\">" + error + "</p>";
        return """
                <!doctype html><html lang="id"><head><meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Akses Manifest Penumpang</title><style>
                *{box-sizing:border-box}body{font-family:system-ui,-apple-system,Segoe UI,sans-serif;
                background:#f3f5f9;display:flex;min-height:100vh;align-items:center;
                justify-content:center;margin:0;color:#18202f}.card{background:white;border-radius:16px;
                box-shadow:0 12px 44px #12233d18;padding:36px;max-width:400px;width:calc(100%% - 32px)}
                h1{font-size:21px;margin:0 0 12px}p{color:#5a6878;font-size:14px;line-height:1.6}
                label{font-weight:600;display:block;margin:22px 0 8px;font-size:14px}
                input{width:100%%;padding:13px;font-size:18px;border:1px solid #c9d1de;border-radius:8px}
                button{margin-top:16px;width:100%%;padding:13px;background:#174b91;color:white;
                border:0;border-radius:8px;font-size:15px;cursor:pointer}
                .err{background:#fff1f0;color:#a41d24;padding:12px;border-radius:8px}
                small{display:block;margin-top:18px;color:#68778a;line-height:1.5}
                </style></head><body><main class="card">
                <h1>Manifest Penumpang Kendaraan</h1>
                <p>Dokumen ini dilindungi password. Masukkan tanggal hari ini dengan format
                <b>DDMMYYYY</b> (contoh: 25122026)</p>
                %s
                <form method="POST" action="/api/v1/manifests/files/%s/unlock">
                <label for="password">Password</label>
                <input id="password" name="password" type="password" inputmode="numeric"
                pattern="[0-9]{8}" maxlength="8" autocomplete="off" required autofocus
                placeholder="DDMMYYYY">
                <button type="submit">Buka PDF</button></form>
                <small>Dokumen hanya dapat dibuka setelah password benar.</small>
                </main></body></html>
                """.formatted(errorHtml, token);
    }

    public record GeneratedPdfResponse(String fileId, String url, String createdAt, String passwordFormat) {}
}
