package id.co.example.manifest.api;

import id.co.example.manifest.dto.GeneratedPdfResponse;
import id.co.example.manifest.dto.ManifestRequest;
import id.co.example.manifest.repository.GeneratedPdfManifestRepository;
import id.co.example.manifest.repository.GeneratedPdfManifest;
import id.co.example.manifest.service.ManifestGenerationService;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
public class RetryGenerateController {
    private final GeneratedPdfManifestRepository generatedManifests;
    private final ManifestGenerationService generationService;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    public RetryGenerateController(GeneratedPdfManifestRepository generatedManifests,
                                   ManifestGenerationService generationService,
                                   JsonMapper jsonMapper, Validator validator) {
        this.generatedManifests = generatedManifests;
        this.generationService = generationService;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @PostMapping(value = "/retry_generate_pdf", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GeneratedPdfResponse> retry(@Valid @RequestBody RetryGenerateRequest request) {
        try {
            GeneratedPdfManifest previous = generatedManifests
                    .findFirstByCutOffIdOrderByCreatedAtDesc(request.cut_off_id())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND,
                            "Data generate manifest tidak ditemukan"));
            ManifestRequest manifest = jsonMapper.readValue(previous.getPayloadGenerate(), ManifestRequest.class)
                    .withIdPkk(request.nomorPkk());
            if (!validator.validate(manifest).isEmpty()) {
                throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                        "Payload generate tersimpan tidak valid");
            }
            return ResponseEntity.status(201)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(generationService.generate(manifest));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Data manifest tidak valid", ex);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR,
                    "Gagal membaca payload atau membuat/menyimpan PDF", ex);
        } catch (DataAccessException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Database generate manifest tidak dapat diakses", ex);
        }
    }

    public record RetryGenerateRequest(
            @NotBlank @Size(max = 100) String cut_off_id,
            @NotBlank @Size(max = 80) String nomorPkk
    ) {}
}
