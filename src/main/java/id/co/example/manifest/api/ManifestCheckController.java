package id.co.example.manifest.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import id.co.example.manifest.repository.DockShip;
import id.co.example.manifest.repository.DockShipRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
public class ManifestCheckController {
    private final DockShipRepository dockShips;

    public ManifestCheckController(DockShipRepository dockShips) {
        this.dockShips = dockShips;
    }

    /** "checkmanifets" is the requested path; "checkmanifest" is its correctly spelled alias. */
    @GetMapping(value = {"/checkmanifets", "/checkmanifest"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ManifestCheckResponse> check(
            @RequestParam("id") @NotBlank @Size(max = 100) String id,
            @RequestParam("branch_id") @Min(0) long branchId,
            @RequestParam("harbour_id") @Min(0) long harbourId,
            @RequestParam("route_id") @Min(0) long routeId) {
        try {
            DockShip row = dockShips.findByIdAndBranchIdAndHarborIdAndRouteId(id, branchId, harbourId, routeId)
                    .orElse(null);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new ManifestCheckResponse(row != null, row));
        } catch (DataAccessException | TransactionException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Database tidak dapat diakses", ex);
        }
    }

    /**
     * Sets dock_ship.tootlip to {@code value} (0 or 1, default 1) for the given id. When nomorPkk is
     * present and non-blank, dock_ship.id_pkk is updated in the same statement. When url_pdf is present
     * and non-blank, dock_ship.manifest_document_url is updated too; approve_manifest is not touched.
     * "manitfestnotapprove" is the requested path; "manifestnotapprove" is its correctly spelled alias.
     */
    @PostMapping(value = {"/manitfestnotapprove", "/manifestnotapprove"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TootlipUpdateResponse> updateTootlip(
            @RequestParam("id") @NotBlank @Size(max = 100) String id,
            @RequestParam(value = "value", defaultValue = "1") int value,
            @RequestParam(value = "nomorPkk", required = false) String nomorPkk,
            @RequestParam(value = "url_pdf", required = false) String urlPdf) {
        // Checked here rather than with @Min/@Max so the rule holds however the controller is set up.
        if (value != 0 && value != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "value harus 0 atau 1");
        }
        String normalizedNomorPkk = nomorPkk == null ? null : nomorPkk.trim();
        String normalizedUrlPdf = urlPdf == null ? null : urlPdf.trim();
        if (normalizedNomorPkk != null && normalizedNomorPkk.length() > 80) {
            throw new ResponseStatusException(BAD_REQUEST, "nomorPkk maksimal 80 karakter");
        }
        try {
            boolean hasNomorPkk = normalizedNomorPkk != null && !normalizedNomorPkk.isEmpty();
            boolean hasUrlPdf = normalizedUrlPdf != null && !normalizedUrlPdf.isEmpty();
            int updateCount;
            if (hasNomorPkk && hasUrlPdf) {
                updateCount = dockShips.updateTootlipAndIdPkkAndManifestDocumentUrl(
                        id, value == 1, normalizedNomorPkk, normalizedUrlPdf);
            } else if (hasNomorPkk) {
                updateCount = dockShips.updateTootlipAndIdPkk(id, value == 1, normalizedNomorPkk);
            } else if (hasUrlPdf) {
                updateCount = dockShips.updateTootlipAndManifestDocumentUrl(id, value == 1, normalizedUrlPdf);
            } else {
                updateCount = dockShips.updateTootlip(id, value == 1);
            }
            boolean updated = updateCount > 0;
            return ResponseEntity.status(updated ? OK : NOT_FOUND)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new TootlipUpdateResponse(id, updated, updated ? value : null));
        } catch (DataAccessException | TransactionException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Database tidak dapat diakses", ex);
        }
    }

    public record ManifestCheckResponse(boolean found, DockShip data) {}

    public record TootlipUpdateResponse(
            String id, boolean updated,
            @JsonProperty("tootlip") Integer tootlip) {}
}
