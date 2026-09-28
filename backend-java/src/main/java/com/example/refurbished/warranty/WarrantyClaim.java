package com.example.refurbished.warranty;

import com.example.refurbished.common.exception.BusinessConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warranty_claims")
public class WarrantyClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warranty_id", nullable = false, updatable = false)
    private Warranty warranty;

    @Column(name = "reported_issue", nullable = false, length = 1000, updatable = false)
    private String reportedIssue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WarrantyClaimStatus status;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WarrantyClaim() {}

    public WarrantyClaim(Warranty warranty, String reportedIssue) {
        if (warranty == null) {
            throw new IllegalArgumentException("Warranty is required.");
        }
        if (reportedIssue == null || reportedIssue.isBlank() || reportedIssue.trim().length() > 1000) {
            throw new IllegalArgumentException("Reported issue is required and must not exceed 1000 characters.");
        }
        this.warranty = warranty;
        this.reportedIssue = reportedIssue.trim();
        this.status = WarrantyClaimStatus.OPEN;
    }

    public void updateStatus(WarrantyClaimStatus newStatus, String resolutionNotes) {
        if (newStatus == null) {
            throw new IllegalArgumentException("Status cannot be null.");
        }
        if (this.status == WarrantyClaimStatus.RESOLVED || this.status == WarrantyClaimStatus.REJECTED) {
            throw new BusinessConflictException("Cannot change status of a closed warranty claim (current status: " + this.status + ").");
        }
        if ((newStatus == WarrantyClaimStatus.RESOLVED || newStatus == WarrantyClaimStatus.REJECTED)
                && (resolutionNotes == null || resolutionNotes.isBlank())) {
            throw new BusinessConflictException("Resolution notes are required when resolving or rejecting a warranty claim.");
        }
        this.status = newStatus;
        if (resolutionNotes != null) {
            this.resolutionNotes = resolutionNotes.trim();
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Warranty getWarranty() { return warranty; }
    public String getReportedIssue() { return reportedIssue; }
    public WarrantyClaimStatus getStatus() { return status; }
    public String getResolutionNotes() { return resolutionNotes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
