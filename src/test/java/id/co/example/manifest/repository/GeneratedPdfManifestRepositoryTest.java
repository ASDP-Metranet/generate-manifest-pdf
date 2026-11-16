package id.co.example.manifest.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GeneratedPdfManifestRepositoryTest {
    @Test
    void insertsTheThreeGenerateFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GeneratedPdfManifestRepository repository = new GeneratedPdfManifestRepository(jdbcTemplate);

        repository.save("CUT-123", "{\"document\":{}}", "PKK-456");

        verify(jdbcTemplate).update(
                contains("INSERT INTO generate_pdf_manifest (cut_off_id, payload_generate, pkk_number)"),
                eq("CUT-123"), eq("{\"document\":{}}"), eq("PKK-456"));
    }
}
