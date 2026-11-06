package id.co.example.manifest.service;

import id.co.example.manifest.dto.ManifestRequest;
import id.co.example.manifest.dto.ManifestRequest.CargoData;
import id.co.example.manifest.dto.ManifestRequest.DocumentData;
import id.co.example.manifest.dto.ManifestRequest.PassengerData;
import id.co.example.manifest.dto.ManifestRequest.SignatureData;
import id.co.example.manifest.dto.ManifestRequest.VehicleData;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ManifestPdfServiceTest {
    private final ManifestPdfService service = new ManifestPdfService(JsonMapper.builder().build());

    @Test
    void qrContainsOnlySpecifiedApprovalFields() {
        String json = service.buildQrJson(document("PKK123213ABC"));
        assertTrue(json.contains("\"id_boarding\":\"B22608190999\""));
        assertTrue(json.contains("\"approved_by_operator\":\"Dharma Lautan Utama\""));
        assertTrue(json.contains("\"tanggal_approve_manifest\":\"2026-08-17T17:17:10\""));
        assertTrue(json.contains("\"id_pkk\":\"PKK123213ABC\""));
        assertFalse(json.contains("manifestId"));
        assertFalse(json.contains("namaKapal"));
    }

    @Test
    void createsPortraitPdfWithAllTemplateSections() throws Exception {
        byte[] bytes = service.generate(request(document("PKK123213ABC"), List.of(vehicle(4)), List.of(), List.of()));
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertEquals(1, pdf.getNumberOfPages());
            PDRectangle size = pdf.getPage(0).getMediaBox();
            assertTrue(size.getHeight() > size.getWidth());
        }
        String text = text(bytes);
        assertTrue(text.contains("DAFTAR PENUMPANG PADA KENDARAAN"));
        assertTrue(text.contains("DAFTAR PENUMPANG PEJALAN KAKI"));
        assertTrue(text.contains("DAFTAR BARANG CURAH DAN HEWAN"));
        assertTrue(text.contains("REKAPITULASI DATA PENUMPANG DAN KENDARAAN"));
        assertTrue(text.contains("Tanggal : 17 Aug 2026"));
        assertTrue(text.contains("Dermaga : Dermaga 1"));
        assertTrue(text.contains("Waktu Berangkat : 22:24"));
    }

    @Test
    void recapCountsPassengersVehiclesAndCargo() throws Exception {
        List<PassengerData> pedestrians = List.of(
                passenger("SITI", "P", "PENUMPANG DEWASA"),
                passenger("BUDI KECIL", "L", "PENUMPANG ANAK"));
        List<CargoData> cargo = List.of(
                new CargoData("HEWAN", "SAPI", "KOTA BOGOR", "3271", 3),
                new CargoData("CURAH", "JAGUNG", "KOTA DEPOK", "3276", 2));
        String text = text(service.generate(request(document("PKK123213ABC"),
                List.of(vehicle(3), vehicle(1)), pedestrians, cargo)));

        assertTrue(text.contains("Jumlah Pejalan Kaki : 2"));
        assertTrue(text.contains("Jumlah Kendaraan : 2"));
        assertTrue(text.contains("Jumlah Penumpang Dalam Kendaraan : 4"));
        assertTrue(text.contains("Jumlah Hewan dan Curah : 5"));
        assertTrue(text.contains("Jumlah Penumpang : 6"));
        assertTrue(text.contains("Penumpang Dewasa"));
        assertTrue(text.contains("Laki-laki : 4 orang"));
        assertTrue(text.contains("Perempuan : 1 orang"));
        assertTrue(text.contains("Penumpang Anak"));
        assertTrue(text.contains("Golongan IV - Penumpang"));
        assertTrue(text.contains("PNP : 4"));
    }

    @Test
    void passengerWithoutKategoriShowsVehicleClassAndCountsAsAdult() throws Exception {
        VehicleData vehicle = new VehicleData("N1234QA", "IV - PENUMPANG",
                List.of(passenger("PENGEMUDI", "L", null)));
        String text = text(service.generate(request(document("PKK123213ABC"), List.of(vehicle), List.of(), List.of())));
        assertTrue(text.contains("GOLONGAN IV -"));
        assertTrue(text.contains("Penumpang Dewasa"));
        assertTrue(text.contains("Laki-laki : 1 orang"));
    }

    @Test
    void longManifestContinuesOnNextPageWithRepeatedHeader() throws Exception {
        byte[] bytes = service.generate(request(document("PKK123213ABC"), List.of(vehicle(60)), List.of(), List.of()));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 1);
        }
        String text = text(bytes);
        assertTrue(text.contains("(LANJUTAN)"));
        assertTrue(text.contains("Jumlah Penumpang Dalam Kendaraan : 60"));
    }

    @Test
    void oversizedRowIsRejectedInsteadOfAddingPagesForever() {
        PassengerData oversized = new PassengerData("A" + "\nB".repeat(70), "L", 30, null, null, null);
        VehicleData vehicle = new VehicleData("N1234QA", "II",
                List.of(oversized, passenger("BERIKUTNYA", "L", null)));
        ManifestRequest request = request(document("PKK123213ABC"), List.of(vehicle), List.of(), List.of());
        assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(IllegalArgumentException.class, () -> service.generate(request)));
    }

    @Test
    void createsPdfWithoutQrWhenIdPkkIsNull() throws Exception {
        DocumentData withoutIdPkk = document(null);
        assertFalse(ManifestPdfService.shouldIncludeQr(withoutIdPkk));

        byte[] withoutQr = service.generate(request(withoutIdPkk, List.of(vehicle(1)), List.of(), List.of()));
        byte[] withQr = service.generate(request(document("PKK123213ABC"), List.of(vehicle(1)), List.of(), List.of()));

        assertEquals("%PDF-", new String(withoutQr, 0, 5, StandardCharsets.US_ASCII));
        assertTrue(withoutQr.length < withQr.length);
        assertTrue(text(withoutQr).contains("AN DHARMA LAUTAN UTAMA"));
    }

    @Test
    void skipsQrWhenIdPkkIsBlank() {
        assertFalse(ManifestPdfService.shouldIncludeQr(document("   ")));
        assertTrue(ManifestPdfService.shouldIncludeQr(document("PKK123213ABC")));
    }

    @Test
    void validationRequiresAtLeastOneManifestEntry() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            assertFalse(validator.validate(new ManifestRequest(
                    document("PKK123213ABC"), null, null, null, signature())).isEmpty());
            assertTrue(validator.validate(new ManifestRequest(
                    document("PKK123213ABC"), null, List.of(passenger("SITI", "P", null)), null, signature())).isEmpty());
        }
    }

    private static String text(byte[] bytes) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
        }
    }

    private static ManifestRequest request(DocumentData document, List<VehicleData> vehicles,
                                           List<PassengerData> pedestrians, List<CargoData> cargo) {
        return new ManifestRequest(document, vehicles, pedestrians, cargo, signature());
    }

    private static VehicleData vehicle(int passengers) {
        List<PassengerData> list = new ArrayList<>();
        for (int i = 0; i < passengers; i++) {
            list.add(passenger("MISDIN HERWAN", "L", "PENUMPANG DEWASA"));
        }
        return new VehicleData("N1234QA", "IV - PENUMPANG", list);
    }

    private static PassengerData passenger(String nama, String jenisKelamin, String kategori) {
        return new PassengerData(nama, jenisKelamin, 30, "KAB. LAMPUNG UTARA", "1234567890123456", kategori);
    }

    private static SignatureData signature() {
        return new SignatureData("DHARMA LAUTAN UTAMA", "Captain Kapal", "DHARMA RUCITRA", null);
    }

    private static DocumentData document(String idPkk) {
        return new DocumentData(
                "MNF-20260817-001", LocalDate.of(2026, 8, 17),
                "DHARMA RUCITRA", "DHARMA LAUTAN UTAMA", "B22608190999",
                "Dharma Lautan Utama", LocalDateTime.of(2026, 8, 17, 17, 17, 10),
                idPkk, "Dermaga 1", null, "22:24");
    }
}
