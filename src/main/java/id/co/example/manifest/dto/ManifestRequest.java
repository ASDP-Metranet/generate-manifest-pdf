package id.co.example.manifest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** JSON contract: same structure and field names as the agreed manifest payload. */
public record ManifestRequest(
        @NotNull @Valid DocumentData document,
        @NotEmpty @Size(max = 10000) List<@NotNull @Valid VehicleData> vehicles,
        @NotNull @Valid SignatureData signature
) {
    public record DocumentData(
            @NotBlank @Size(max=80) String manifestId,
            @NotNull LocalDate tanggal,
            @NotBlank @Size(max=120) String namaKapal,
            @NotBlank @Size(max=120) String namaOperator,
            @NotBlank @Size(max=80) String id_boarding,
            @NotBlank @Size(max=120) String approved_by_operator,
            @NotNull LocalDateTime tanggal_approve_manifest,
            @Size(max=80) String id_pkk
    ) {}

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
            @Size(max=150) String keterangan
    ) {}

    public record SignatureData(
            @NotBlank @Size(max=120) String atasNama,
            @NotBlank @Size(max=100) String jabatan,
            @NotBlank @Size(max=120) String namaKapal,
            @Size(max=80) String qrVerificationId
    ) {}
}
