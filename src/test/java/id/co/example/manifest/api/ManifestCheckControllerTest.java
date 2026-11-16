package id.co.example.manifest.api;

import id.co.example.manifest.repository.DockShip;
import id.co.example.manifest.repository.DockShipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.ToIntBiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ManifestCheckControllerTest {
    private static final String QUERY = "?id=ABC&branch_id=1011&harbour_id=348&route_id=5510";

    private static final DockShip ROW = new DockShip("ABC", 1011L, 348L, 5510L);

    @Test
    void returnsRowWithDatabaseColumnNamesWhenAllParametersMatch() throws Exception {
        mvc(() -> Optional.of(ROW))
                .perform(get("/checkmanifets" + QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.data.id").value("ABC"))
                .andExpect(jsonPath("$.data.branch_id").value(1011))
                .andExpect(jsonPath("$.data.harbor_id").value(348))
                .andExpect(jsonPath("$.data.route_id").value(5510))
                .andExpect(jsonPath("$.data.harborId").doesNotExist())
                .andExpect(jsonPath("$.data.cut_off_id").value("ABC"))
                .andExpect(jsonPath("$.data.cutOffId").doesNotExist());
    }

    @Test
    void responseIncludesTheNewManifestColumns() throws Exception {
        var result = mvc(() -> Optional.of(ROW))
                .perform(get("/checkmanifets" + QUERY))
                .andExpect(status().isOk());
        for (String column : new String[]{"tootlip", "id_pkk", "manifest_id", "id_boarding",
                "approved_by_operator", "manifest_approved_at", "manifest_document_url"}) {
            result.andExpect(jsonPath("$.data." + column).hasJsonPath());
        }
        result.andExpect(jsonPath("$.data.manifestDocumentUrl").doesNotExist());
    }

    @Test
    void correctlySpelledPathWorksToo() throws Exception {
        mvc(() -> Optional.of(ROW))
                .perform(get("/checkmanifest" + QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true));
    }

    @Test
    void reportsNotFoundWithoutData() throws Exception {
        mvc(Optional::empty)
                .perform(get("/checkmanifets" + QUERY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void rejectsMissingOrNonNumericParameters() throws Exception {
        MockMvc mvc = mvc(Optional::empty);
        mvc.perform(get("/checkmanifets?id=ABC&branch_id=1011&harbour_id=348"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/checkmanifets?id=ABC&branch_id=abc&harbour_id=348&route_id=5510"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseFailureReturnsServiceUnavailable() throws Exception {
        mvc(() -> { throw new DataAccessResourceFailureException("down"); })
                .perform(get("/checkmanifets" + QUERY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void valueParameterSetsTootlip() throws Exception {
        List<String> updates = new ArrayList<>();
        MockMvc mvc = mvc(Optional::empty, (id, value) -> { updates.add(id + "=" + value); return 1; });
        mvc.perform(post("/manitfestnotapprove").param("id", "ABC").param("value", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ABC"))
                .andExpect(jsonPath("$.updated").value(true))
                .andExpect(jsonPath("$.tootlip").value(0))
                .andExpect(jsonPath("$.approve_manifest").doesNotExist());
        mvc.perform(post("/manitfestnotapprove").param("id", "ABC").param("value", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tootlip").value(1));
        assertEquals(List.of("ABC=false", "ABC=true"), updates);
    }

    @Test
    void valueDefaultsToOneWhenNotSent() throws Exception {
        List<String> updates = new ArrayList<>();
        mvc(Optional::empty, (id, value) -> { updates.add(id + "=" + value); return 1; })
                .perform(post("/manitfestnotapprove").param("id", "ABC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tootlip").value(1));
        assertEquals(List.of("ABC=true"), updates);
    }

    @Test
    void nomorPkkUpdatesIdPkkTogetherWithTootlip() throws Exception {
        List<String> updates = new ArrayList<>();
        mvc(Optional::empty, (id, value) -> { throw new AssertionError("wrong update method"); },
                (id, value, nomorPkk) -> {
                    updates.add(id + "=" + value + "," + nomorPkk);
                    return 1;
                })
                .perform(post("/manifestnotapprove")
                        .param("id", "ABC")
                        .param("value", "0")
                        .param("nomorPkk", "  PKK-123  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(true))
                .andExpect(jsonPath("$.tootlip").value(0));
        assertEquals(List.of("ABC=false,PKK-123"), updates);
    }

    @Test
    void blankNomorPkkDoesNotOverwriteExistingIdPkk() throws Exception {
        List<String> updates = new ArrayList<>();
        mvc(Optional::empty, (id, value) -> { updates.add(id + "=" + value); return 1; },
                (id, value, nomorPkk) -> { throw new AssertionError("must not overwrite id_pkk"); })
                .perform(post("/manifestnotapprove")
                        .param("id", "ABC")
                        .param("nomorPkk", "   "))
                .andExpect(status().isOk());
        assertEquals(List.of("ABC=true"), updates);
    }

    @Test
    void nomorPkkLongerThanDatabaseFieldIsRejected() throws Exception {
        mvc(Optional::empty, (id, value) -> { throw new AssertionError("must not update"); },
                (id, value, nomorPkk) -> { throw new AssertionError("must not update"); })
                .perform(post("/manifestnotapprove")
                        .param("id", "ABC")
                        .param("nomorPkk", "X".repeat(81)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void urlPdfUpdatesManifestDocumentUrlTogetherWithTootlip() throws Exception {
        List<String> updates = new ArrayList<>();
        mvc(Optional::empty,
                (id, value) -> { throw new AssertionError("wrong update method"); },
                (id, value, nomorPkk) -> { throw new AssertionError("must not update id_pkk"); },
                (id, value, urlPdf) -> {
                    updates.add(id + "=" + value + "," + urlPdf);
                    return 1;
                },
                (id, value, nomorPkk, urlPdf) -> { throw new AssertionError("wrong update method"); })
                .perform(post("/manifestnotapprove")
                        .param("id", "ABC")
                        .param("value", "0")
                        .param("url_pdf", "  https://pdf.example.com/manifest/ABC.pdf  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tootlip").value(0));
        assertEquals(List.of("ABC=false,https://pdf.example.com/manifest/ABC.pdf"), updates);
    }

    @Test
    void nomorPkkAndUrlPdfAreUpdatedTogether() throws Exception {
        List<String> updates = new ArrayList<>();
        mvc(Optional::empty,
                (id, value) -> { throw new AssertionError("wrong update method"); },
                (id, value, nomorPkk) -> { throw new AssertionError("wrong update method"); },
                (id, value, urlPdf) -> { throw new AssertionError("wrong update method"); },
                (id, value, nomorPkk, urlPdf) -> {
                    updates.add(id + "=" + value + "," + nomorPkk + "," + urlPdf);
                    return 1;
                })
                .perform(post("/manifestnotapprove")
                        .param("id", "ABC")
                        .param("nomorPkk", "PKK-123")
                        .param("url_pdf", "https://pdf.example.com/manifest/ABC.pdf"))
                .andExpect(status().isOk());
        assertEquals(List.of("ABC=true,PKK-123,https://pdf.example.com/manifest/ABC.pdf"), updates);
    }

    @Test
    void blankUrlPdfDoesNotOverwriteExistingManifestDocumentUrl() throws Exception {
        List<String> updates = new ArrayList<>();
        mvc(Optional::empty,
                (id, value) -> { updates.add(id + "=" + value); return 1; },
                (id, value, nomorPkk) -> { throw new AssertionError("must not update id_pkk"); },
                (id, value, urlPdf) -> { throw new AssertionError("must not update URL"); },
                (id, value, nomorPkk, urlPdf) -> { throw new AssertionError("must not update optional fields"); })
                .perform(post("/manifestnotapprove")
                        .param("id", "ABC")
                        .param("url_pdf", "   "))
                .andExpect(status().isOk());
        assertEquals(List.of("ABC=true"), updates);
    }

    @Test
    void valueOtherThanZeroOrOneIsRejected() throws Exception {
        MockMvc mvc = mvc(Optional::empty, (id, value) -> { throw new AssertionError("must not update"); });
        for (String value : new String[]{"2", "-1", "10", "abc", "1.5"}) {
            mvc.perform(post("/manitfestnotapprove").param("id", "ABC").param("value", value))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void notApproveReportsUnknownId() throws Exception {
        mvc(Optional::empty, (id, value) -> 0)
                .perform(post("/manitfestnotapprove").param("id", "TIDAKADA").param("value", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.updated").value(false))
                .andExpect(jsonPath("$.tootlip").isEmpty());
    }

    @Test
    void notApproveRequiresIdAndPost() throws Exception {
        MockMvc mvc = mvc(Optional::empty, (id, value) -> { throw new AssertionError("must not update"); });
        mvc.perform(post("/manitfestnotapprove")).andExpect(status().isBadRequest());
        mvc.perform(get("/manitfestnotapprove?id=ABC")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void notApproveDatabaseFailureReturnsServiceUnavailable() throws Exception {
        mvc(Optional::empty, (id, value) -> { throw new DataAccessResourceFailureException("down"); })
                .perform(post("/manitfestnotapprove").param("id", "ABC"))
                .andExpect(status().isServiceUnavailable());
    }

    private static MockMvc mvc(Supplier<Optional<DockShip>> result) {
        return mvc(result, (id, value) -> { throw new AssertionError("must not update"); });
    }

    private static MockMvc mvc(Supplier<Optional<DockShip>> result, ToIntBiFunction<String, Boolean> update) {
        return mvc(result, update, (id, value, nomorPkk) -> {
            throw new AssertionError("must not update id_pkk");
        });
    }

    private static MockMvc mvc(Supplier<Optional<DockShip>> result, ToIntBiFunction<String, Boolean> update,
                               PkkUpdate updateWithPkk) {
        return mvc(result, update, updateWithPkk,
                (id, value, urlPdf) -> { throw new AssertionError("must not update URL"); },
                (id, value, nomorPkk, urlPdf) -> { throw new AssertionError("must not update URL"); });
    }

    private static MockMvc mvc(Supplier<Optional<DockShip>> result, ToIntBiFunction<String, Boolean> update,
                               PkkUpdate updateWithPkk, UrlUpdate updateWithUrl, FullUpdate updateWithBoth) {
        DockShipRepository repository = new DockShipRepository() {
            @Override
            public Optional<DockShip> findByIdAndBranchIdAndHarborIdAndRouteId(
                    String id, Long branchId, Long harborId, Long routeId) {
                return result.get();
            }

            @Override
            public int updateTootlip(String id, boolean value) {
                return update.applyAsInt(id, value);
            }

            @Override
            public int updateTootlipAndIdPkk(String id, boolean value, String nomorPkk) {
                return updateWithPkk.apply(id, value, nomorPkk);
            }

            @Override
            public int updateTootlipAndManifestDocumentUrl(String id, boolean value, String urlPdf) {
                return updateWithUrl.apply(id, value, urlPdf);
            }

            @Override
            public int updateTootlipAndIdPkkAndManifestDocumentUrl(
                    String id, boolean value, String nomorPkk, String urlPdf) {
                return updateWithBoth.apply(id, value, nomorPkk, urlPdf);
            }
        };
        return MockMvcBuilders.standaloneSetup(new ManifestCheckController(repository)).build();
    }

    @FunctionalInterface
    private interface PkkUpdate {
        int apply(String id, boolean value, String nomorPkk);
    }

    @FunctionalInterface
    private interface UrlUpdate {
        int apply(String id, boolean value, String urlPdf);
    }

    @FunctionalInterface
    private interface FullUpdate {
        int apply(String id, boolean value, String nomorPkk, String urlPdf);
    }
}
