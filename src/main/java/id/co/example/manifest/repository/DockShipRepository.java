package id.co.example.manifest.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Extends the bare Repository, so only the methods declared here exist: no save or delete.
 * The only write is resetting approve_manifest.
 */
public interface DockShipRepository extends Repository<DockShip, String> {
    @Transactional(readOnly = true)
    Optional<DockShip> findByIdAndBranchIdAndHarborIdAndRouteId(String id, Long branchId, Long harborId, Long routeId);

    /** Returns the number of matched rows: 1 when the id exists, also when it was already 0. */
    @Transactional
    @Modifying
    @Query("UPDATE DockShip d SET d.approveManifest = false WHERE d.id = :id")
    int resetApproveManifest(@Param("id") String id);
}
