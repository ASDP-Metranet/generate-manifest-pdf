package id.co.example.manifest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * JSON contract: vehicles, pedestrians and cargo are optional lists and may all be empty;
 * the PDF then shows the three tables with headers only and a recap of zeros.
 */
public record ManifestRequest(
        @NotNull @Valid DocumentData document,
        @Size(max = 10000) List<@NotNull @Valid VehicleData> vehicles,
        @Size(max = 10000) List<@NotNull @Valid PassengerData> pedestrians,
        @Size(max = 10000) List<@NotNull @Valid CargoData> cargo,
        @NotNull @Valid SignatureData signature
) {
    public ManifestRequest {
        vehicles = vehicles == null ? List.of() : vehicles;
        pedestrians = pedestrians == null ? List.of() : pedestrians;
        cargo = cargo == null ? List.of() : cargo;
    }

    /**
     * cutOffId is a digit string that starts with its date, e.g. 20241023143733008048400 (YYYYMMDD + more digits).
     * Only those first 8 digits are used: they set the PDF password, written DDMMYYYY.
     */
    public record DocumentData(
            @NotBlank @Pattern(regexp = "\\d{8,100}", message = "cutOffId harus angka 8-100 digit, diawali tanggal YYYYMMDD")
            String cutOffId,
            @NotBlank @Size(max=80) String manifestId,
            @NotNull LocalDate tanggal,
            @NotBlank @Size(max=120) String namaKapal,
            @NotBlank @Size(max=120) String namaOperator,
            @NotBlank @Size(max=80) String id_boarding,
            @NotBlank @Size(max=120) String approved_by_operator,
            @NotNull LocalDateTime tanggal_approve_manifest,
            @Size(max=80) String id_pkk,
            @Size(max=120) String dermaga,
            @Size(max=20) String waktuTiba,
            @Size(max=20) String waktuBerangkat
    ) {
        private static final DateTimeFormatter CUT_OFF_FORMAT =
                DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

        /** Rejects a cutOffId whose first 8 digits are not a real date, such as 20261332... Format errors come from @Pattern. */
        @AssertTrue(message = "8 digit awal cutOffId bukan tanggal yang valid (YYYYMMDD)")
        public boolean isCutOffIdRealDate() {
            if (cutOffId == null || !cutOffId.matches("\\d{8,100}")) {
                return true;
            }
            try {
                LocalDate.parse(cutOffId.substring(0, 8), CUT_OFF_FORMAT);
                return true;
            } catch (DateTimeParseException ex) {
                return false;
            }
        }
    }

    public record VehicleData(
            @NotBlank @Size(max=32) String nomorKendaraan,
            @NotBlank @Size(max=20) String golongan,
            @NotEmpty List<@NotNull @Valid PassengerData> passengers
    ) {}

    public record PassengerData(
            @Size(max=150) String nama,
            @NotBlank @Size(max=2) String jenisKelamin,
            @NotNull @Min(0) @Max(130) Integer usia,
            @Size(max=180) String alamatDomisili,
            @Size(max=100) String nomorIdentitas,
            @Size(max=60) String kategori
    ) {}

    public record CargoData(
            @NotBlank @Size(max=60) String golongan,
            @NotBlank @Size(max=150) String nama,
            @Size(max=180) String alamatDomisili,
            @Size(max=100) String nomorIdentitas,
            @NotNull @Min(1) @Max(1000000) Integer qty
    ) {}

    public record SignatureData(
            @NotBlank @Size(max=120) String atasNama,
            @NotBlank @Size(max=100) String jabatan,
            @NotBlank @Size(max=120) String namaKapal,
            @Size(max=80) String qrVerificationId
    ) {}
}
