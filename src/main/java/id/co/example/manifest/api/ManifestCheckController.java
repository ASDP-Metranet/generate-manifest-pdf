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
     * Sets dock_ship.approve_manifest = 0 for the given id. "manitfestnotapprove" is the requested
     * path; "manifestnotapprove" is its correctly spelled alias.
     */
    @PostMapping(value = {"/manitfestnotapprove", "/manifestnotapprove"}, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApproveManifestResetResponse> resetApproveManifest(
            @RequestParam("id") @NotBlank @Size(max = 100) String id) {
        try {
            boolean updated = dockShips.resetApproveManifest(id) > 0;
            return ResponseEntity.status(updated ? OK : NOT_FOUND)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(new ApproveManifestResetResponse(id, updated, updated ? 0 : null));
        } catch (DataAccessException | TransactionException ex) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Database tidak dapat diakses", ex);
        }
    }

    public record ManifestCheckResponse(boolean found, DockShip data) {}

    public record ApproveManifestResetResponse(
            String id, boolean updated,
            @JsonProperty("approve_manifest") Integer approveManifest) {}
}
