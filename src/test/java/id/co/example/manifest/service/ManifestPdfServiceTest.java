package id.co.example.manifest.service;

import id.co.example.manifest.dto.ManifestRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestPdfServiceTest {
    private final ManifestPdfService service = new ManifestPdfService(JsonMapper.builder().build());

    @Test
    void qrContainsOnlySpecifiedApprovalFields() {
        ManifestRequest.DocumentData document = document();
        String json = service.buildQrJson(document);
        assertTrue(json.contains("\"id_boarding\":\"B22608190999\""));
        assertTrue(json.contains("\"approved_by_operator\":\"Dharma Lautan Utama\""));
        assertTrue(json.contains("\"tanggal_approve_manifest\":\"2026-08-17T17:17:10\""));
        assertTrue(json.contains("\"id_pkk\":\"PKK123213ABC\""));
        assertFalse(json.contains("manifestId"));
        assertFalse(json.contains("namaKapal"));
    }

    @Test
    void createsNonEmptyPdfFromRequest() throws Exception {
        ManifestRequest.PassengerData passenger = new ManifestRequest.PassengerData(
                "MISDIN HERWAN", "L", 30, "KAB. LAMPUNG UTARA", "1234567890123456", null);
        ManifestRequest.VehicleData vehicle = new ManifestRequest.VehicleData(
                "B1234ABC", "II", List.of(passenger));
        ManifestRequest.SignatureData signature = new ManifestRequest.SignatureData(
                "DHARMA LAUTAN UTAMA", "Captain Kapal", "DHARMA RUCITRA", "MNF-20260817-001");
        byte[] bytes = service.generate(new ManifestRequest(document(), List.of(vehicle), signature));
        assertTrue(bytes.length > 1000);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    void createsPdfWithoutQrWhenIdPkkIsNull() throws Exception {
        ManifestRequest.PassengerData passenger = new ManifestRequest.PassengerData(
                "MISDIN HERWAN", "L", 30, "KAB. LAMPUNG UTARA", "1234567890123456", null);
        ManifestRequest.VehicleData vehicle = new ManifestRequest.VehicleData(
                "B1234ABC", "II", List.of(passenger));
        ManifestRequest.SignatureData signature = new ManifestRequest.SignatureData(
                "DHARMA LAUTAN UTAMA", "Captain Kapal", "DHARMA RUCITRA", null);
        ManifestRequest.DocumentData withoutIdPkk = new ManifestRequest.DocumentData(
                "MNF-20260817-001", LocalDate.of(2026, 8, 17),
                "DHARMA RUCITRA", "DHARMA LAUTAN UTAMA", "B22608190999",
                "Dharma Lautan Utama", LocalDateTime.of(2026, 8, 17, 17, 17, 10), null);

        assertFalse(ManifestPdfService.shouldIncludeQr(withoutIdPkk));
        byte[] bytes = service.generate(new ManifestRequest(withoutIdPkk, List.of(vehicle), signature));

        assertTrue(bytes.length > 1000);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    void skipsQrWhenIdPkkIsBlank() {
        ManifestRequest.DocumentData blankIdPkk = new ManifestRequest.DocumentData(
                "MNF-20260817-001", LocalDate.of(2026, 8, 17),
                "DHARMA RUCITRA", "DHARMA LAUTAN UTAMA", "B22608190999",
                "Dharma Lautan Utama", LocalDateTime.of(2026, 8, 17, 17, 17, 10), "   ");

        assertFalse(ManifestPdfService.shouldIncludeQr(blankIdPkk));
        assertTrue(ManifestPdfService.shouldIncludeQr(document()));
    }

    private static ManifestRequest.DocumentData document() {
        return new ManifestRequest.DocumentData(
                "MNF-20260817-001", LocalDate.of(2026, 8, 17),
                "DHARMA RUCITRA", "DHARMA LAUTAN UTAMA", "B22608190999",
                "Dharma Lautan Utama", LocalDateTime.of(2026, 8, 17, 17, 17, 10),
                "PKK123213ABC");
    }
}
