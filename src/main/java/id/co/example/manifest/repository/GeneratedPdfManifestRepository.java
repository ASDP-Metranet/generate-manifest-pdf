package id.co.example.manifest.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Writes the request payload associated with a successfully generated manifest PDF. */
@Repository
public class GeneratedPdfManifestRepository {
    private static final String INSERT_SQL = """
            INSERT INTO generate_pdf_manifest (cut_off_id, payload_generate, pkk_number)
            VALUES (?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public GeneratedPdfManifestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(String cutOffId, String payloadGenerate, String pkkNumber) {
        jdbcTemplate.update(INSERT_SQL, cutOffId, payloadGenerate, pkkNumber);
    }
}
