package com.example.refurbished.warranty.dto;

import com.example.refurbished.warranty.WarrantyClaim;
import com.example.refurbished.warranty.WarrantyClaimStatus;
import java.time.Instant;
import java.util.UUID;

public record WarrantyClaimResponse(
        UUID id,
        UUID warrantyId,
        UUID deviceUnitId,
        String reportedIssue,
        WarrantyClaimStatus status,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt
) {
    public static WarrantyClaimResponse from(WarrantyClaim claim) {
        return new WarrantyClaimResponse(
                claim.getId(),
                claim.getWarranty().getId(),
                claim.getWarranty().getDeviceUnit().getId(),
                claim.getReportedIssue(),
                claim.getStatus(),
                claim.getResolutionNotes(),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
