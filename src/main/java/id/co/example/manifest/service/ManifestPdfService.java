package id.co.example.manifest.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import id.co.example.manifest.dto.ManifestRequest;
import id.co.example.manifest.dto.ManifestRequest.CargoData;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A4 portrait layout following the manifest template: passengers in vehicles, pedestrians,
 * bulk cargo and animals, then the recap with signature and QR. Layout coordinates are in PDF
 * points measured from the top of the page. The QR holds exactly the four approval properties
 * as plain text, one "name: value" line each, so a phone scanner shows them readably.
 */
@Service
public class ManifestPdfService {
    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float PAGE_H = PAGE.getHeight();
    private static final float LEFT = 34.9f;
    private static final float RIGHT = 561.4f;
    private static final float WIDTH = RIGHT - LEFT;
    private static final float SPLIT = 266.6f;
    private static final float TOP = 34.9f;
    private static final float BOTTOM = PAGE_H - 34.9f;
    private static final float TABLE_GAP = 15.7f;
    private static final float TITLE_H = 18.8f;
    private static final float ROW_H = 15.8f;
    private static final float LINE_H = 13.8f;
    private static final float DETAIL_LINE_H = 16.5f;
    private static final float SIGNATURE_H = 116f;
    private static final float QR_SIZE = 70f;
    private static final float CELL_PAD = 1.1f;
    private static final float TITLE_SIZE = 14f;
    private static final float FONT_SIZE = 12f;
    private static final float SIGNATURE_SIZE = 9f;
    private static final PDFont NORMAL = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final DateTimeFormatter DOCUMENT_DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter QR_TIMESTAMP = DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm:ss");
    private static final String DEFAULT_CATEGORY = "PENUMPANG DEWASA";

    private static final Table VEHICLE_TABLE = new Table("DAFTAR PENUMPANG PADA KENDARAAN",
            new float[]{LEFT, 55.1f, 148.1f, 166.1f, 190.9f, 286.1f, 384.4f, 436.9f, 533.6f, RIGHT},
            new String[]{"No.", "Nama", "JK", "Usia", "Alamat", "Identitas", "Nopol", "Golongan", "Tot\nPnp"},
            2 * LINE_H + 1.6f);
    private static final Table PEDESTRIAN_TABLE = new Table("DAFTAR PENUMPANG PEJALAN KAKI",
            new float[]{LEFT, 91.9f, 186.4f, 237.4f, 308.6f, 424.1f, RIGHT},
            new String[]{"No.", "Nama", "JK", "Usia", "Alamat", "Identitas"},
            ROW_H);
    private static final Table CARGO_TABLE = new Table("DAFTAR BARANG CURAH DAN HEWAN",
            new float[]{LEFT, 83.6f, 211.9f, 292.9f, 391.9f, 508.1f, RIGHT},
            new String[]{"No.", "Golongan", "Nama", "Alamat", "Identitas", "Qty"},
            ROW_H);

    public byte[] generate(ManifestRequest request) throws IOException {
        int vehiclePassengers = request.vehicles().stream().mapToInt(v -> v.passengers().size()).sum();
        if (vehiclePassengers + request.pedestrians().size() + request.cargo().size() > 10000) {
            throw new IllegalArgumentException("Maksimum 10.000 baris data dalam satu dokumen");
        }

        DocumentData document = request.document();
        List<Row> vehicleRows = vehicleRows(request.vehicles());
        List<Row> pedestrianRows = pedestrianRows(request.pedestrians());
        List<Row> cargoRows = cargoRows(request.cargo());
        Recap recap = buildRecap(request);
        String qrText = shouldIncludeQr(document) ? buildQrText(document) : null;

        try (PDDocument pdf = new PDDocument();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            try (Sheet sheet = new Sheet(pdf)) {
                drawTable(sheet, document, VEHICLE_TABLE, vehicleRows);
                drawTable(sheet, document, PEDESTRIAN_TABLE, pedestrianRows);
                drawTable(sheet, document, CARGO_TABLE, cargoRows);
                drawRecap(sheet, document, recap, request.signature(), qrText);
            }
            pdf.save(buffer);
            return buffer.toByteArray();
        }
    }

    /**
     * QR content, for example:
     * <pre>
     * id_boarding: B22608190999
     * approved_by_operator: Dharma Lautan Utama
     * tanggal_approve_manifest: 2026/08/17 17:17:10
     * id_pkk: PKK123213ABC
     * </pre>
     */
    public String buildQrText(DocumentData d) {
        return String.join("\n",
                "id_boarding: " + oneLine(d.id_boarding()),
                "approved_by_operator: " + oneLine(d.approved_by_operator()),
                "tanggal_approve_manifest: " + d.tanggal_approve_manifest().format(QR_TIMESTAMP),
                "id_pkk: " + oneLine(d.id_pkk()));
    }

    private List<Row> vehicleRows(List<VehicleData> vehicles) throws IOException {
        List<Row> rows = new ArrayList<>();
        int number = 0;
        for (VehicleData v : vehicles) {
            for (PassengerData p : v.passengers()) {
                number++;
                String golongan = isBlank(p.kategori()) ? golonganLabel(v.golongan()) : upper(p.kategori());
                rows.add(row(VEHICLE_TABLE, String.valueOf(number), safe(p.nama()), upper(p.jenisKelamin()),
                        String.valueOf(p.usia()), safe(p.alamatDomisili()), safe(p.nomorIdentitas()),
                        upper(v.nomorKendaraan()), golongan, "1"));
            }
        }
        return rows;
    }

    private List<Row> pedestrianRows(List<PassengerData> pedestrians) throws IOException {
        List<Row> rows = new ArrayList<>();
        int number = 0;
        for (PassengerData p : pedestrians) {
            number++;
            rows.add(row(PEDESTRIAN_TABLE, String.valueOf(number), safe(p.nama()), upper(p.jenisKelamin()),
                    String.valueOf(p.usia()), safe(p.alamatDomisili()), safe(p.nomorIdentitas())));
        }
        return rows;
    }

    private List<Row> cargoRows(List<CargoData> cargo) throws IOException {
        List<Row> rows = new ArrayList<>();
        int number = 0;
        for (CargoData c : cargo) {
            number++;
            rows.add(row(CARGO_TABLE, String.valueOf(number), upper(c.golongan()), safe(c.nama()),
                    safe(c.alamatDomisili()), safe(c.nomorIdentitas()), String.valueOf(c.qty())));
        }
        return rows;
    }

    private Row row(Table table, String... columns) throws IOException {
        List<List<String>> cells = new ArrayList<>();
        int maxLines = 1;
        for (int i = 0; i < columns.length; i++) {
            float cellWidth = table.columns()[i + 1] - table.columns()[i];
            List<String> lines = wrap(columns[i], NORMAL, FONT_SIZE, cellWidth - 2 * CELL_PAD);
            cells.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }
        float height = Math.max(ROW_H, maxLines * LINE_H + 1.6f);
        if (height > BOTTOM - TOP - headHeight(table)) {
            throw new IllegalArgumentException("Satu baris data terlalu tinggi untuk area PDF");
        }
        return new Row(cells, height);
    }

    private Recap buildRecap(ManifestRequest request) {
        Map<String, Map<String, Integer>> passengers = new LinkedHashMap<>();
        Map<String, int[]> vehicles = new LinkedHashMap<>();
        int inVehicles = 0;
        for (VehicleData v : request.vehicles()) {
            int[] counts = vehicles.computeIfAbsent(golonganLabel(v.golongan()), unused -> new int[2]);
            counts[0]++;
            counts[1] += v.passengers().size();
            inVehicles += v.passengers().size();
            v.passengers().forEach(p -> countPassenger(passengers, p));
        }
        request.pedestrians().forEach(p -> countPassenger(passengers, p));
        long cargoTotal = request.cargo().stream().mapToLong(CargoData::qty).sum();
        return new Recap(passengers, vehicles, request.pedestrians().size(), inVehicles,
                request.vehicles().size(), cargoTotal);
    }

    /** Passengers without kategori, such as a driver listed under the vehicle class, count as adults. */
    private void countPassenger(Map<String, Map<String, Integer>> passengers, PassengerData p) {
        String category = isBlank(p.kategori()) ? DEFAULT_CATEGORY : upper(p.kategori());
        passengers.computeIfAbsent(category, unused -> new TreeMap<>())
                .merge(genderLabel(p.jenisKelamin()), 1, Integer::sum);
    }

    private void drawTable(Sheet sheet, DocumentData document, Table table, List<Row> rows) throws IOException {
        float firstRow = rows.isEmpty() ? 0f : rows.getFirst().height();
        sheet.reserve(headHeight(table) + firstRow);
        drawHead(sheet, document, table, false);
        for (Row row : rows) {
            if (sheet.y + row.height() > BOTTOM) {
                sheet.newPage();
                drawHead(sheet, document, table, true);
            }
            drawRow(sheet, table, row);
        }
    }

    private void drawHead(Sheet sheet, DocumentData d, Table table, boolean continuation) throws IOException {
        drawTitle(sheet, table.title());
        drawFullRow(sheet, dateLine(d));
        drawFullRow(sheet, shipLine(d) + (continuation ? " (LANJUTAN)" : ""));
        float[] x = table.columns();
        float top = sheet.y;
        float height = table.headerHeight();
        box(sheet, LEFT, top, WIDTH, height);
        for (int i = 0; i < table.headers().length; i++) {
            if (i > 0) vertical(sheet, x[i], top, top + height);
            String[] lines = table.headers()[i].split("\\n");
            float textTop = top + (height - lines.length * LINE_H) / 2f;
            for (int n = 0; n < lines.length; n++) {
                center(sheet, BOLD, FONT_SIZE, (x[i] + x[i + 1]) / 2f, textTop + n * LINE_H + 10.6f, lines[n]);
            }
        }
        sheet.y += height;
    }

    private void drawRow(Sheet sheet, Table table, Row row) throws IOException {
        float[] x = table.columns();
        float top = sheet.y;
        box(sheet, LEFT, top, WIDTH, row.height());
        for (int col = 0; col < row.cells().size(); col++) {
            if (col > 0) vertical(sheet, x[col], top, top + row.height());
            List<String> lines = row.cells().get(col);
            float textTop = top + (row.height() - lines.size() * LINE_H) / 2f;
            for (int n = 0; n < lines.size(); n++) {
                text(sheet, NORMAL, FONT_SIZE, x[col] + CELL_PAD, textTop + n * LINE_H + 10.6f, lines.get(n));
            }
        }
        sheet.y += row.height();
    }

    private void drawRecap(Sheet sheet, DocumentData d, Recap recap, SignatureData signature, String qrText)
            throws IOException {
        List<DetailLine> left = passengerLines(recap);
        List<DetailLine> right = vehicleLines(recap);
        int detailLines = Math.max(3, Math.max(left.size(), right.size()));
        float detailHeight = detailLines * DETAIL_LINE_H + 3.7f;
        float total = TITLE_H + 6 * ROW_H + detailHeight + SIGNATURE_H;
        if (total > BOTTOM - TOP) {
            throw new IllegalArgumentException("Rekapitulasi terlalu panjang untuk area PDF");
        }
        sheet.reserve(total);

        drawTitle(sheet, "REKAPITULASI DATA PENUMPANG DAN KENDARAAN");
        drawFullRow(sheet, dateLine(d));
        drawSplitRow(sheet, shipLine(d), "Waktu Tiba : " + oneLine(d.waktuTiba()));
        drawSplitRow(sheet, "Dermaga : " + oneLine(d.dermaga()), "Waktu Berangkat : " + oneLine(d.waktuBerangkat()));

        float top = sheet.y;
        box(sheet, LEFT, top, WIDTH, detailHeight);
        vertical(sheet, SPLIT, top, top + detailHeight);
        drawDetailLines(sheet, left, top);
        drawDetailLines(sheet, right, top);
        sheet.y += detailHeight;

        drawSplitRow(sheet, "Jumlah Pejalan Kaki : " + recap.pedestrians(),
                "Jumlah Kendaraan : " + recap.vehicleCount());
        drawSplitRow(sheet, "Jumlah Penumpang Dalam Kendaraan : " + recap.inVehicles(),
                "Jumlah Hewan dan Curah : " + recap.cargoTotal());
        drawSplitRow(sheet, "Jumlah Penumpang : " + (recap.pedestrians() + recap.inVehicles()), "");
        drawSignatureAndQr(sheet, signature, qrText);
    }

    private List<DetailLine> passengerLines(Recap recap) throws IOException {
        List<DetailLine> lines = new ArrayList<>();
        lines.add(new DetailLine(38.2f, "PENUMPANG"));
        float maxWidth = SPLIT - 38.2f - CELL_PAD;
        for (Map.Entry<String, Map<String, Integer>> category : recap.passengers().entrySet()) {
            for (String line : wrap(titleCase(category.getKey()), NORMAL, FONT_SIZE, maxWidth)) {
                lines.add(new DetailLine(38.2f, line));
            }
            for (Map.Entry<String, Integer> gender : category.getValue().entrySet()) {
                lines.add(new DetailLine(45f, gender.getKey() + " : " + gender.getValue() + " orang"));
            }
        }
        return lines;
    }

    private List<DetailLine> vehicleLines(Recap recap) throws IOException {
        List<DetailLine> lines = new ArrayList<>();
        lines.add(new DetailLine(270.2f, "KENDARAAN"));
        for (Map.Entry<String, int[]> golongan : recap.vehicles().entrySet()) {
            List<String> label = wrap(titleCase(golongan.getKey()), NORMAL, FONT_SIZE, 467.5f - 277f - 2f);
            for (int n = 0; n < label.size(); n++) {
                if (n == 0) {
                    int[] counts = golongan.getValue();
                    lines.add(new DetailLine(277f, label.get(n), ": " + counts[0], "PNP : " + counts[1]));
                } else {
                    lines.add(new DetailLine(277f, label.get(n)));
                }
            }
        }
        return lines;
    }

    private void drawDetailLines(Sheet sheet, List<DetailLine> lines, float top) throws IOException {
        for (int n = 0; n < lines.size(); n++) {
            DetailLine line = lines.get(n);
            float baseline = top + 14f + n * DETAIL_LINE_H;
            text(sheet, NORMAL, FONT_SIZE, line.x(), baseline, line.label());
            if (line.count() != null) {
                text(sheet, NORMAL, FONT_SIZE, 467.5f, baseline, line.count());
                text(sheet, NORMAL, FONT_SIZE, 493f, baseline, line.passengers());
            }
        }
    }

    private void drawSignatureAndQr(Sheet sheet, SignatureData s, String qrText) throws IOException {
        float top = sheet.y;
        box(sheet, LEFT, top, WIDTH, SIGNATURE_H);
        vertical(sheet, SPLIT, top, top + SIGNATURE_H);
        float center = (SPLIT + RIGHT) / 2f;
        float maxWidth = RIGHT - SPLIT - 8f;
        centerFitted(sheet, NORMAL, center, top + 13f, maxWidth, "AN " + upper(s.atasNama()));
        centerFitted(sheet, NORMAL, center, top + 24f, maxWidth, oneLine(s.jabatan()) + " " + oneLine(s.namaKapal()));
        if (qrText != null) {
            drawQr(sheet, qrText, center - QR_SIZE / 2f, top + 28f);
        }
        centerFitted(sheet, BOLD, center, top + 109f, maxWidth, upper(s.atasNama()));
        sheet.y += SIGNATURE_H;
    }

    private void drawQr(Sheet sheet, String qrText, float x, float top) throws IOException {
        try {
            BitMatrix bits = new QRCodeWriter().encode(qrText, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                           EncodeHintType.MARGIN, 2));
            float module = QR_SIZE / bits.getWidth();
            float bottom = PAGE_H - top - QR_SIZE;
            // One PDF rectangle per contiguous black run: crisp/scalable QR without image artifacts.
            for (int yy = 0; yy < bits.getHeight(); yy++) {
                int xx = 0;
                while (xx < bits.getWidth()) {
                    while (xx < bits.getWidth() && !bits.get(xx, yy)) xx++;
                    int start = xx;
                    while (xx < bits.getWidth() && bits.get(xx, yy)) xx++;
                    if (start < xx) {
                        sheet.cs.addRect(x + start * module,
                                bottom + QR_SIZE - (yy + 1) * module,
                                (xx - start) * module + 0.015f, module + 0.015f);
                        sheet.cs.fill();
                    }
                }
            }
        } catch (WriterException e) {
            throw new IOException("Gagal membuat QR Code manifest", e);
        }
    }

    private void drawTitle(Sheet sheet, String title) throws IOException {
        box(sheet, LEFT, sheet.y, WIDTH, TITLE_H);
        center(sheet, BOLD, TITLE_SIZE, (LEFT + RIGHT) / 2f, sheet.y + 14f, title);
        sheet.y += TITLE_H;
    }

    private void drawFullRow(Sheet sheet, String value) throws IOException {
        box(sheet, LEFT, sheet.y, WIDTH, ROW_H);
        fitted(sheet, LEFT + CELL_PAD, sheet.y + 12f, WIDTH - 2 * CELL_PAD, value);
        sheet.y += ROW_H;
    }

    private void drawSplitRow(Sheet sheet, String left, String right) throws IOException {
        box(sheet, LEFT, sheet.y, WIDTH, ROW_H);
        vertical(sheet, SPLIT, sheet.y, sheet.y + ROW_H);
        fitted(sheet, LEFT + CELL_PAD, sheet.y + 12f, SPLIT - LEFT - 2 * CELL_PAD, left);
        fitted(sheet, SPLIT + CELL_PAD, sheet.y + 12f, RIGHT - SPLIT - 2 * CELL_PAD, right);
        sheet.y += ROW_H;
    }

    private String dateLine(DocumentData d) {
        return "Tanggal : " + d.tanggal().format(DOCUMENT_DATE);
    }

    private String shipLine(DocumentData d) {
        return "Nama Kapal : " + upper(d.namaKapal());
    }

    private static float headHeight(Table table) {
        return TITLE_H + 2 * ROW_H + table.headerHeight();
    }

    private String golonganLabel(String golongan) {
        String label = upper(golongan);
        return label.startsWith("GOLONGAN") ? label : "GOLONGAN " + label;
    }

    private String genderLabel(String gender) {
        String value = upper(gender);
        return switch (value) {
            case "L" -> "Laki-laki";
            case "P" -> "Perempuan";
            default -> value;
        };
    }

    /** "GOLONGAN IV - PENUMPANG" becomes "Golongan IV - Penumpang"; Roman numerals stay uppercase. */
    private String titleCase(String value) {
        List<String> words = new ArrayList<>();
        for (String word : value.split(" ")) {
            if (word.isEmpty() || word.matches("[IVX]+[A-Z]?")) {
                words.add(word);
            } else {
                words.add(word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return String.join(" ", words);
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
        return normalized.replace('–', '-').replace('—', '-')
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"');
    }

    private String oneLine(String s) {
        return safe(s).replace('\n', ' ').trim();
    }

    private String upper(String s) {
        return oneLine(s).toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    static boolean shouldIncludeQr(DocumentData document) {
        return document.id_pkk() != null && !document.id_pkk().isBlank();
    }

    private float measure(PDFont f, float size, String value) throws IOException {
        return f.getStringWidth(value) / 1000f * size;
    }
    private void text(Sheet sheet, PDFont font, float size, float x, float baseline, String value)
            throws IOException {
        sheet.cs.beginText();
        sheet.cs.setFont(font, size);
        sheet.cs.newLineAtOffset(x, PAGE_H - baseline);
        sheet.cs.showText(value);
        sheet.cs.endText();
    }
    private void center(Sheet sheet, PDFont font, float size, float centerX, float baseline, String value)
            throws IOException {
        text(sheet, font, size, centerX - measure(font, size, value) / 2f, baseline, value);
    }
    /** Single-line cells shrink instead of overflowing, so long values stay inside the table. */
    private void fitted(Sheet sheet, float x, float baseline, float maxWidth, String value) throws IOException {
        text(sheet, NORMAL, fittedSize(NORMAL, FONT_SIZE, maxWidth, value), x, baseline, value);
    }
    private void centerFitted(Sheet sheet, PDFont font, float centerX, float baseline, float maxWidth, String value)
            throws IOException {
        center(sheet, font, fittedSize(font, SIGNATURE_SIZE, maxWidth, value), centerX, baseline, value);
    }
    private float fittedSize(PDFont font, float size, float maxWidth, String value) throws IOException {
        float width = measure(font, size, value);
        return width <= maxWidth ? size : size * maxWidth / width;
    }
    private void box(Sheet sheet, float x, float top, float width, float height) throws IOException {
        sheet.cs.setLineWidth(0.75f);
        sheet.cs.addRect(x, PAGE_H - top - height, width, height);
        sheet.cs.stroke();
    }
    private void vertical(Sheet sheet, float x, float top, float bottom) throws IOException {
        sheet.cs.setLineWidth(0.75f);
        sheet.cs.moveTo(x, PAGE_H - top);
        sheet.cs.lineTo(x, PAGE_H - bottom);
        sheet.cs.stroke();
    }

    private record Table(String title, float[] columns, String[] headers, float headerHeight) {}
    private record Row(List<List<String>> cells, float height) {}
    private record DetailLine(float x, String label, String count, String passengers) {
        DetailLine(float x, String label) {
            this(x, label, null, null);
        }
    }
    private record Recap(Map<String, Map<String, Integer>> passengers, Map<String, int[]> vehicles,
                         int pedestrians, int inVehicles, int vehicleCount, long cargoTotal) {}

    /** Current page and vertical position, measured from the top of the page. */
    private static final class Sheet implements AutoCloseable {
        private final PDDocument pdf;
        private PDPageContentStream cs;
        private float y;

        Sheet(PDDocument pdf) throws IOException {
            this.pdf = pdf;
            newPage();
        }

        void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PAGE);
            pdf.addPage(page);
            cs = new PDPageContentStream(pdf, page);
            y = TOP;
        }

        /** Keeps a block whole: separates it from the previous one, or moves it to a new page. */
        void reserve(float height) throws IOException {
            if (y == TOP) return;
            if (y + TABLE_GAP + height > BOTTOM) {
                newPage();
            } else {
                y += TABLE_GAP;
            }
        }

        @Override
        public void close() throws IOException {
            if (cs != null) {
                cs.close();
                cs = null;
            }
        }
    }
}
