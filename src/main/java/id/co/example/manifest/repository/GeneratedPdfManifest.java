package id.co.example.manifest.repository;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "generate_pdf_manifest")
public class GeneratedPdfManifest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "cut_off_id", nullable = false)
    private String cutOffId;

    @Column(name = "payload_generate", nullable = false)
    private String payloadGenerate;

    @Column(name = "pkk_number")
    private String pkkNumber;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected GeneratedPdfManifest() {}

    public GeneratedPdfManifest(String cutOffId, String payloadGenerate, String pkkNumber) {
        this.cutOffId = cutOffId;
        this.payloadGenerate = payloadGenerate;
        this.pkkNumber = pkkNumber;
    }

    public Long getId() { return id; }
    public String getCutOffId() { return cutOffId; }
    public String getPayloadGenerate() { return payloadGenerate; }
    public String getPkkNumber() { return pkkNumber; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
