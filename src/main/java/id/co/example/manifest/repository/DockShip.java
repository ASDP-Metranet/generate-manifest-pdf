package id.co.example.manifest.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Mapping of dock_ship. JSON names keep the database column names. There are no setters:
 * the only write is DockShipRepository.updateTootlip.
 */
@Entity
@Table(name = "dock_ship")
public class DockShip {
    @Id
    @JsonProperty("id")
    private String id;

    @Column(name = "branch_id")
    @JsonProperty("branch_id")
    private Long branchId;

    @Column(name = "harbor_id")
    @JsonProperty("harbor_id")
    private Long harborId;

    @Column(name = "route_id")
    @JsonProperty("route_id")
    private Long routeId;

    @Column(name = "ship_id")
    @JsonProperty("ship_id")
    private Integer shipId;

    @Column(name = "dock_id")
    @JsonProperty("dock_id")
    private Integer dockId;

    @Column(name = "dock_price")
    @JsonProperty("dock_price")
    private Double dockPrice;

    @Column(name = "ship_grt")
    @JsonProperty("ship_grt")
    private Double shipGrt;

    @Column(name = "bea_call")
    @JsonProperty("bea_call")
    private Integer beaCall;

    @Column(name = "bea_sandar")
    @JsonProperty("bea_sandar")
    private Double beaSandar;

    @Column(name = "bea_kepil")
    @JsonProperty("bea_kepil")
    private Integer beaKepil;

    @Column(name = "gs_shift")
    @JsonProperty("gs_shift")
    private String gsShift;

    @Column(name = "gs_regu")
    @JsonProperty("gs_regu")
    private String gsRegu;

    @Column(name = "gs_rekap")
    @JsonProperty("gs_rekap")
    private LocalDateTime gsRekap;

    @Column(name = "dock_time")
    @JsonProperty("dock_time")
    private LocalDateTime dockTime;

    @Column(name = "created_at")
    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @Column(name = "pend_hewan")
    @JsonProperty("pend_hewan")
    private Double pendHewan;

    @Column(name = "pend_curah")
    @JsonProperty("pend_curah")
    private Double pendCurah;

    @Column(name = "pend_pos")
    @JsonProperty("pend_pos")
    private Double pendPos;

    @Column(name = "updated_at")
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "approve_manifest")
    @JsonProperty("approve_manifest")
    private Boolean approveManifest;

    // The column really is spelled "tootlip" in the database.
    @Column(name = "tootlip")
    @JsonProperty("tootlip")
    private Boolean tootlip;

    @Column(name = "id_pkk")
    @JsonProperty("id_pkk")
    private String idPkk;

    @Column(name = "manifest_id")
    @JsonProperty("manifest_id")
    private String manifestId;

    @Column(name = "id_boarding")
    @JsonProperty("id_boarding")
    private String idBoarding;

    @Column(name = "approved_by_operator")
    @JsonProperty("approved_by_operator")
    private String approvedByOperator;

    @Column(name = "manifest_approved_at")
    @JsonProperty("manifest_approved_at")
    private LocalDateTime manifestApprovedAt;

    @Column(name = "manifest_document_url")
    @JsonProperty("manifest_document_url")
    private String manifestDocumentUrl;

    protected DockShip() {
    }

    // Test fixture only; production rows come from the database.
    public DockShip(String id, Long branchId, Long harborId, Long routeId) {
        this.id = id;
        this.branchId = branchId;
        this.harborId = harborId;
        this.routeId = routeId;
    }

    public String getId() { return id; }
    public Long getBranchId() { return branchId; }
    public Long getHarborId() { return harborId; }
    public Long getRouteId() { return routeId; }
    public Integer getShipId() { return shipId; }
    public Integer getDockId() { return dockId; }
    public Double getDockPrice() { return dockPrice; }
    public Double getShipGrt() { return shipGrt; }
    public Integer getBeaCall() { return beaCall; }
    public Double getBeaSandar() { return beaSandar; }
    public Integer getBeaKepil() { return beaKepil; }
    public String getGsShift() { return gsShift; }
    public String getGsRegu() { return gsRegu; }
    public LocalDateTime getGsRekap() { return gsRekap; }
    public LocalDateTime getDockTime() { return dockTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public Double getPendHewan() { return pendHewan; }
    public Double getPendCurah() { return pendCurah; }
    public Double getPendPos() { return pendPos; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public Boolean getApproveManifest() { return approveManifest; }
    public Boolean getTootlip() { return tootlip; }
    public String getIdPkk() { return idPkk; }
    public String getManifestId() { return manifestId; }
    public String getIdBoarding() { return idBoarding; }
    public String getApprovedByOperator() { return approvedByOperator; }
    public LocalDateTime getManifestApprovedAt() { return manifestApprovedAt; }
    public String getManifestDocumentUrl() { return manifestDocumentUrl; }

    /**
     * Kept in the JSON for existing clients. The cut_off_id column was dropped from dock_ship,
     * so it now carries the row id, which equals the id the client asked for.
     */
    @JsonProperty("cut_off_id")
    public String getCutOffId() { return id; }
}
