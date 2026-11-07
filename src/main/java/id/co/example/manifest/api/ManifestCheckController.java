package id.co.example.manifest.api;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    public record ManifestCheckResponse(boolean found, DockShip data) {}
}
