package id.co.example.manifest.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Extends the bare Repository, so only the methods declared here exist: no save or delete.
 * Writes are limited to tootlip and the optional manifest metadata updated with it.
 */
public interface DockShipRepository extends Repository<DockShip, String> {
    @Transactional(readOnly = true)
    Optional<DockShip> findByIdAndBranchIdAndHarborIdAndRouteId(String id, Long branchId, Long harborId, Long routeId);

    /** Sets dock_ship.tootlip to the given value. Returns the number of matched rows: 1 when the id exists, also when the value was unchanged. */
    @Transactional
    @Modifying
    @Query("UPDATE DockShip d SET d.tootlip = :value WHERE d.id = :id")
    int updateTootlip(@Param("id") String id, @Param("value") boolean value);

    /** Atomically updates tootlip and id_pkk when nomorPkk is supplied by the caller. */
    @Transactional
    @Modifying
    @Query("UPDATE DockShip d SET d.tootlip = :value, d.idPkk = :nomorPkk WHERE d.id = :id")
    int updateTootlipAndIdPkk(@Param("id") String id, @Param("value") boolean value,
                              @Param("nomorPkk") String nomorPkk);

    /** Atomically updates tootlip and manifest_document_url when url_pdf is supplied. */
    @Transactional
    @Modifying
    @Query("UPDATE DockShip d SET d.tootlip = :value, d.manifestDocumentUrl = :urlPdf WHERE d.id = :id")
    int updateTootlipAndManifestDocumentUrl(@Param("id") String id, @Param("value") boolean value,
                                            @Param("urlPdf") String urlPdf);

    /** Atomically updates tootlip, id_pkk and manifest_document_url when both optional values are supplied. */
    @Transactional
    @Modifying
    @Query("UPDATE DockShip d SET d.tootlip = :value, d.idPkk = :nomorPkk, "
            + "d.manifestDocumentUrl = :urlPdf WHERE d.id = :id")
    int updateTootlipAndIdPkkAndManifestDocumentUrl(
            @Param("id") String id, @Param("value") boolean value,
            @Param("nomorPkk") String nomorPkk, @Param("urlPdf") String urlPdf);
}
