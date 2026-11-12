package id.co.example.manifest.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Extends the bare Repository, so only the methods declared here exist: no save or delete.
 * The only write is setting tootlip.
 */
public interface DockShipRepository extends Repository<DockShip, String> {
    @Transactional(readOnly = true)
    Optional<DockShip> findByIdAndBranchIdAndHarborIdAndRouteId(String id, Long branchId, Long harborId, Long routeId);

    /** Sets dock_ship.tootlip to the given value. Returns the number of matched rows: 1 when the id exists, also when the value was unchanged. */
    @Transactional
    @Modifying
    @Query("UPDATE DockShip d SET d.tootlip = :value WHERE d.id = :id")
    int updateTootlip(@Param("id") String id, @Param("value") boolean value);
}
