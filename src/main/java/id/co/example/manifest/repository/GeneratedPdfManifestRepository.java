package id.co.example.manifest.repository;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/** ORM repository for generated manifest payload history. */
public interface GeneratedPdfManifestRepository extends Repository<GeneratedPdfManifest, Long> {
    GeneratedPdfManifest save(GeneratedPdfManifest manifest);

    Optional<GeneratedPdfManifest> findFirstByCutOffIdOrderByCreateAtDesc(String cutOffId);
}
