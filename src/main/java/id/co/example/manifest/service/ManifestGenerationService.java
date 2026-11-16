package id.co.example.manifest.service;

import id.co.example.manifest.dto.GeneratedPdfResponse;
import id.co.example.manifest.dto.ManifestRequest;
import id.co.example.manifest.repository.GeneratedPdfManifestRepository;
import id.co.example.manifest.repository.GeneratedPdfManifest;
import id.co.example.manifest.security.PdfPasswordGate;
import id.co.example.manifest.storage.LocalPdfStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Runs the complete generation flow shared by initial generation and retry. */
@Service
public class ManifestGenerationService {
    private final ManifestPdfService generator;
    private final LocalPdfStorage storage;
    private final GeneratedPdfManifestRepository generatedManifests;
    private final JsonMapper jsonMapper;
    private final String publicBaseUrl;
    private final ZoneId timezone;

    public ManifestGenerationService(ManifestPdfService generator, LocalPdfStorage storage,
                                     GeneratedPdfManifestRepository generatedManifests, JsonMapper jsonMapper,
                                     @Value("${manifest.public-base-url:http://localhost:8080}") String publicBaseUrl,
                                     @Value("${manifest.access.zone:Asia/Jakarta}") String timezone) {
        this.generator = generator;
        this.storage = storage;
        this.generatedManifests = generatedManifests;
        this.jsonMapper = jsonMapper;
        this.timezone = ZoneId.of(timezone);
        String configured = publicBaseUrl.replaceAll("/+$", "");
        URI base = URI.create(configured);
        if (base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null
                || !("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))) {
            throw new IllegalArgumentException("manifest.public-base-url harus URL HTTP(S) valid");
        }
        this.publicBaseUrl = configured;
    }

    public GeneratedPdfResponse generate(ManifestRequest request) throws IOException {
        byte[] pdf = generator.generate(request);
        String token = storage.save(pdf, PdfPasswordGate.digest(pdfPassword(request.document().cutOffId())));
        generatedManifests.save(new GeneratedPdfManifest(request.document().cutOffId(),
                jsonMapper.writeValueAsString(request), request.document().id_pkk()));
        String url = publicBaseUrl + "/api/v1/manifests/files/" + token;
        return new GeneratedPdfResponse(token, url, OffsetDateTime.now(timezone).toString(), "DDMMYYYY");
    }

    /** Converts the YYYYMMDD prefix of cutOffId into the PDF password DDMMYYYY. */
    public static String pdfPassword(String cutOffId) {
        return cutOffId.substring(6, 8) + cutOffId.substring(4, 6) + cutOffId.substring(0, 4);
    }
}
