package id.co.example.manifest.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import id.co.example.manifest.dto.ManifestRequest;
import id.co.example.manifest.dto.ManifestRequest.DocumentData;
import id.co.example.manifest.dto.ManifestRequest.PassengerData;
import id.co.example.manifest.dto.ManifestRequest.SignatureData;
import id.co.example.manifest.dto.ManifestRequest.VehicleData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Coordinates are in PDF points, origin bottom-left. Draws a vector, selectable-text PDF.
 * The QR is intentionally exactly the four approval properties, serialized as JSON.
 */
@Service
public class ManifestPdfService {
    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float PAGE_W = PAGE.getWidth();
    private static final float PAGE_H = PAGE.getHeight();
    private static final float LEFT = 14f;
    private static final float RIGHT = PAGE_W - LEFT;
    private static final float WIDTH = RIGHT - LEFT;
    private static final float HEADER_ROW_H = 37f;
    private static final float[] COLUMN_WEIGHTS = {33, 134, 29, 42, 109, 123, 90, 105, 49, 99};
    private static final String[] HEADERS = {
            "NO", "NAMA", "JK", "USIA", "ALAMAT\n(DOMISILI)",
            "NOMER IDENTITAS", "NO\nKENDARAAN", "GOL", "TOTAL\nPNP", "KETERANGAN"
    };
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.forLanguageTag("id-ID"));
    private static final DateTimeFormatter QR_TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private static final PDFont NORMAL = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final float FONT_SIZE = 10.0f;
    private static final float LINE_H = 11.9f;

    private final JsonMapper jsonMapper;

    public ManifestPdfService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public byte[] generate(ManifestRequest request) throws IOException {
        if (request.vehicles().stream().mapToInt(v -> v.passengers().size()).sum() > 10000) {
            throw new IllegalArgumentException("Maksimum 10.000 penumpang dalam satu dokumen");
        }

        List<Row> rows = flattenRows(request);
        String qrJson = shouldIncludeQr(request.document())
                ? buildQrJson(request.document())
                : null;
        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            int offset = 0;
            int pageNo = 0;
            while (offset < rows.size()) {
                int pageStartOffset = offset;
                pageNo++;
                PDPage page = new PDPage(PAGE);
                pdf.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(pdf, page)) {
                    cs.setStrokingColor(Color.BLACK);
                    cs.setNonStrokingColor(Color.BLACK);
                    float y = drawHeader(cs, request.document(), pageNo > 1);

                    // If all remaining rows fit above the signature area, this is the last page.
                    float remainder = 0f;
                    for (int i = offset; i < rows.size(); i++) remainder += rows.get(i).height;
                    if (remainder <= y - 218f) {
                        while (offset < rows.size()) y = drawRow(cs, rows.get(offset++), y);
                        drawSignatureAndQr(cs, request.signature(), qrJson);
                        break;
                    }

                    // Otherwise use most of the current page and continue on another page.
                    // Preserve at least one passenger for the following page.
                    while (offset < rows.size() - 1 && y - rows.get(offset).height >= 32f) {
                        y = drawRow(cs, rows.get(offset++), y);
                    }
                    if (offset == rows.size() - 1 && y - rows.get(offset).height < 218f) {
                        // The last row is reserved for the following page with its QR footer.
                        // A single oversized row would otherwise cause an endless page loop.
                        if (offset == pageStartOffset) {
                            throw new IOException("Satu baris data terlalu tinggi untuk area PDF");
                        }
                        continue;
                    }
                    if (offset == rows.size() - 1 && y - rows.get(offset).height >= 218f) {
                        y = drawRow(cs, rows.get(offset++), y);
                        drawSignatureAndQr(cs, request.signature(), qrJson);
                        break;
                    }
                    if (offset == rows.size()) {
                        drawSignatureAndQr(cs, request.signature(), qrJson);
                        break;
                    }
                }
            }
            pdf.save(buffer);
            return buffer.toByteArray();
        }
    }

    public String buildQrJson(DocumentData d) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id_boarding", d.id_boarding());
        payload.put("approved_by_operator", d.approved_by_operator());
        payload.put("tanggal_approve_manifest", d.tanggal_approve_manifest().format(QR_TIMESTAMP));
        payload.put("id_pkk", d.id_pkk());
        return jsonMapper.writeValueAsString(payload);
    }

    private List<Row> flattenRows(ManifestRequest request) throws IOException {
        List<Row> result = new ArrayList<>();
        int number = 0;
        for (VehicleData v : request.vehicles()) {
            for (PassengerData p : v.passengers()) {
                number++;
                String golongan = safe(v.golongan()).toUpperCase(Locale.ROOT);
                if (!golongan.startsWith("GOLONGAN")) golongan = "GOLONGAN " + golongan;
                String[] columns = {
                        String.valueOf(number), safe(p.nama()), safe(p.jenisKelamin()).toUpperCase(Locale.ROOT),
                        String.valueOf(p.usia()), safe(p.alamatDomisili()), safe(p.nomorIdentitas()),
                        safe(v.nomorKendaraan()).toUpperCase(Locale.ROOT), golongan, "1", safe(p.keterangan())
                };
                List<List<String>> cellLines = new ArrayList<>();
                int maxLines = 1;
                for (int i = 0; i < columns.length; i++) {
                    List<String> wrapped = wrap(columns[i], NORMAL, FONT_SIZE, columnWidth(i) - 9f);
                    cellLines.add(wrapped);
                    maxLines = Math.max(maxLines, wrapped.size());
                }
                result.add(new Row(cellLines, Math.max(27f, 8f + maxLines * LINE_H)));
            }
        }
        return result;
    }

    private float drawHeader(PDPageContentStream cs, DocumentData d, boolean continuation) throws IOException {
        center(cs, BOLD, 12.8f, PAGE_W / 2f, PAGE_H - 28.5f,
                "DAFTAR PENUMPANG PADA KENDARAAN");
        float top = PAGE_H - 41f;
        float h = 23.5f;
        drawBox(cs, LEFT, top - h, WIDTH, h);
        text(cs, NORMAL, 10.5f, LEFT + 7f, top - 16f,
                "Tanggal : " + d.tanggal().format(LONG_DATE));
        top -= h;
        drawBox(cs, LEFT, top - h, WIDTH, h);
        text(cs, NORMAL, 10.5f, LEFT + 7f, top - 16f,
                "Nama Kapal : " + safe(d.namaKapal()).toUpperCase(Locale.ROOT)
                        + (continuation ? " (LANJUTAN)" : ""));
        top -= h;
        drawBox(cs, LEFT, top - HEADER_ROW_H, WIDTH, HEADER_ROW_H);
        for (int i = 0; i < HEADERS.length; i++) {
            if (i > 0) vertical(cs, colX(i), top, top - HEADER_ROW_H);
            String[] headerLines = HEADERS[i].split("\\n");
            float middle = top - HEADER_ROW_H / 2f;
            for (int line = 0; line < headerLines.length; line++) {
                float baseline = middle + (headerLines.length - 1) * 5.2f - line * 10.4f - 3.3f;
                center(cs, NORMAL, 9.3f, colX(i) + columnWidth(i) / 2f, baseline, headerLines[line]);
            }
        }
        return top - HEADER_ROW_H;
    }

    private float drawRow(PDPageContentStream cs, Row r, float top) throws IOException {
        float bottom = top - r.height;
        drawBox(cs, LEFT, bottom, WIDTH, r.height);
        for (int i = 1; i < COLUMN_WEIGHTS.length; i++) vertical(cs, colX(i), top, bottom);
        for (int col = 0; col < r.cells.size(); col++) {
            List<String> lines = r.cells.get(col);
            float topBaseline = top - ((r.height - lines.size() * LINE_H) / 2f) - 9.8f;
            for (int n = 0; n < lines.size(); n++) {
                float baseline = topBaseline - n * LINE_H;
                if (col == 0 || col == 2 || col == 3) {
                    center(cs, NORMAL, FONT_SIZE, colX(col) + columnWidth(col) / 2f, baseline, lines.get(n));
                } else {
                    text(cs, NORMAL, FONT_SIZE, colX(col) + 5f, baseline, lines.get(n));
                }
            }
        }
        return bottom;
    }

    private void drawSignatureAndQr(PDPageContentStream cs, SignatureData s, String qrJson) throws IOException {
        float center = RIGHT - 103f;
        center(cs, NORMAL, 10f, center, 194f, "AN " + safe(s.atasNama()).toUpperCase(Locale.ROOT));
        center(cs, NORMAL, 9.5f, center, 179f,
                safe(s.jabatan()) + " " + safe(s.namaKapal()));
        if (qrJson != null) {
            float qrSize = 103f;
            try {
                BitMatrix bits = new QRCodeWriter().encode(qrJson, BarcodeFormat.QR_CODE, 0, 0,
                        Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                               EncodeHintType.MARGIN, 2));
                float module = qrSize / bits.getWidth();
                float qrX = center - qrSize / 2f;
                float qrY = 67f;
                // One PDF rectangle per contiguous black run: crisp/scalable QR without image artifacts.
                for (int yy = 0; yy < bits.getHeight(); yy++) {
                    int xx = 0;
                    while (xx < bits.getWidth()) {
                        while (xx < bits.getWidth() && !bits.get(xx, yy)) xx++;
                        int start = xx;
                        while (xx < bits.getWidth() && bits.get(xx, yy)) xx++;
                        if (start < xx) {
                            cs.addRect(qrX + start * module,
                                    qrY + qrSize - (yy + 1) * module,
                                    (xx - start) * module + 0.015f, module + 0.015f);
                            cs.fill();
                        }
                    }
                }
            } catch (WriterException e) {
                throw new IOException("Gagal membuat QR Code manifest", e);
            }
        }
        center(cs, BOLD, 9.8f, center, 54f,
                safe(s.atasNama()).toUpperCase(Locale.ROOT));
    }

    private List<String> wrap(String s, PDFont font, float size, float maxWidth) throws IOException {
        List<String> out = new ArrayList<>();
        String clean = safe(s).trim();
        if (clean.isEmpty()) return List.of("");
        for (String rawLine : clean.split("\\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : rawLine.split("\\s+")) {
                if (word.isEmpty()) continue;
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (measure(font, size, candidate) <= maxWidth) {
                    current = new StringBuilder(candidate);
                } else {
                    if (!current.isEmpty()) { out.add(current.toString()); current.setLength(0); }
                    if (measure(font, size, word) <= maxWidth) {
                        current.append(word);
                    } else {
                        for (int k = 0; k < word.length(); k++) {
                            String candidatePart = current.toString() + word.charAt(k);
                            if (!current.isEmpty() && measure(font, size, candidatePart) > maxWidth) {
                                out.add(current.toString());
                                current.setLength(0);
                            }
                            current.append(word.charAt(k));
                        }
                    }
                }
            }
            out.add(current.toString());
        }
        return out;
    }

    private String safe(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").replaceAll("[\\p{Cntrl}&&[^\\n]]", " ");
        return normalized.replace('\u2013', '-').replace('\u2014', '-')
                .replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201C', '"').replace('\u201D', '"');
    }

    static boolean shouldIncludeQr(DocumentData document) {
        return document.id_pkk() != null && !document.id_pkk().isBlank();
    }

    private float measure(PDFont f, float size, String value) throws IOException {
        return f.getStringWidth(value) / 1000f * size;
    }
    private float columnWidth(int i) {
        float sum = 0f;
        for (float w : COLUMN_WEIGHTS) sum += w;
        return WIDTH * COLUMN_WEIGHTS[i] / sum;
    }
    private float colX(int i) {
        float x = LEFT;
        for (int k = 0; k < i; k++) x += columnWidth(k);
        return x;
    }
    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(value));
        cs.endText();
    }
    private void center(PDPageContentStream cs, PDFont font, float size, float centerX, float y, String value)
            throws IOException {
        String clean = safe(value);
        text(cs, font, size, centerX - measure(font, size, clean) / 2f, y, clean);
    }
    private void drawBox(PDPageContentStream cs, float x, float y, float width, float height) throws IOException {
        cs.setLineWidth(0.65f);
        cs.addRect(x, y, width, height);
        cs.stroke();
    }
    private void vertical(PDPageContentStream cs, float x, float y1, float y2) throws IOException {
        cs.setLineWidth(0.65f);
        cs.moveTo(x, y1);
        cs.lineTo(x, y2);
        cs.stroke();
    }
    private record Row(List<List<String>> cells, float height) {}
}
