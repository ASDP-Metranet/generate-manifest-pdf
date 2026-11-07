package id.co.example.manifest.repository;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/** Extends the bare Repository, so only the read method declared here exists. */
public interface DockShipRepository extends Repository<DockShip, String> {
    Optional<DockShip> findByIdAndBranchIdAndHarborIdAndRouteId(String id, Long branchId, Long harborId, Long routeId);
}
