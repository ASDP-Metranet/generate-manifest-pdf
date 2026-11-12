package id.co.example.manifest.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManifestPdfControllerTest {
    @Test
    void pdfPasswordIsCutOffIdWrittenDayFirst() {
        assertEquals("25122026", ManifestPdfController.pdfPassword("20261225"));
        assertEquals("29102026", ManifestPdfController.pdfPassword("20261029"));
        assertEquals("01012027", ManifestPdfController.pdfPassword("20270101"));
        // cutOffId from the front end: only the first 8 digits (the date) count.
        assertEquals("23102024", ManifestPdfController.pdfPassword("20241023143733008048400"));
    }
}
