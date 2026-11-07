package id.co.example.manifest.api;

import id.co.example.manifest.repository.DockShip;
import id.co.example.manifest.repository.DockShipRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                .andExpect(jsonPath("$.data.harborId").doesNotExist());
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

    private static MockMvc mvc(Supplier<Optional<DockShip>> result) {
        DockShipRepository repository = (id, branchId, harborId, routeId) -> result.get();
        return MockMvcBuilders.standaloneSetup(new ManifestCheckController(repository)).build();
    }
}
