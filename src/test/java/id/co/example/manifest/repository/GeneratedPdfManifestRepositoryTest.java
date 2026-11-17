package id.co.example.manifest.repository;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeneratedPdfManifestRepositoryTest {
    @Test
    void mapsTheThreeGenerateFieldsToTheEntity() {
        GeneratedPdfManifest manifest = new GeneratedPdfManifest(
                "CUT-123", "{\"document\":{}}", "PKK-456");

        assertEquals("CUT-123", manifest.getCutOffId());
        assertEquals("{\"document\":{}}", manifest.getPayloadGenerate());
        assertEquals("PKK-456", manifest.getPkkNumber());
    }

    @Test
    void exposesDerivedOrmQueryForNewestCreatedAt() throws Exception {
        assertNotNull(GeneratedPdfManifestRepository.class.getMethod(
                "findFirstByCutOffIdOrderByCreatedAtDesc", String.class));
    }
}
