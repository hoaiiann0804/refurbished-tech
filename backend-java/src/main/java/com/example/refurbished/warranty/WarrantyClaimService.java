package com.example.refurbished.warranty;

import com.example.refurbished.audit.AuditService;
import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.ResourceNotFoundException;
import com.example.refurbished.warranty.dto.CreateWarrantyClaimRequest;
import com.example.refurbished.warranty.dto.UpdateWarrantyClaimStatusRequest;
import com.example.refurbished.warranty.dto.WarrantyClaimResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WarrantyClaimService {

    private final WarrantyRepository warranties;
    private final WarrantyClaimRepository claims;
    private final AuditService audit;

    public WarrantyClaimService(WarrantyRepository warranties, WarrantyClaimRepository claims, AuditService audit) {
        this.warranties = warranties;
        this.claims = claims;
        this.audit = audit;
    }

    @Transactional
    public WarrantyClaimResponse create(UUID warrantyId, CreateWarrantyClaimRequest request) {
        Warranty warranty = warranties.findById(warrantyId)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty not found."));

        if (warranty.isExpired()) {
            throw new BusinessConflictException("Warranty expired on " + warranty.getEndsOn() + ".");
        }

        WarrantyClaim claim = new WarrantyClaim(warranty, request.reportedIssue());
        claims.saveAndFlush(claim);

        audit.record("WARRANTY_CLAIM_CREATED", "WARRANTY_CLAIM", claim.getId(),
                Map.of("warrantyId", warrantyId, "status", claim.getStatus().name()));
        return WarrantyClaimResponse.from(claim);
    }

    @Transactional
    public WarrantyClaimResponse updateStatus(UUID claimId, UpdateWarrantyClaimStatusRequest request) {
        WarrantyClaim claim = claims.findByIdForUpdate(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty claim not found."));

        claim.updateStatus(request.status(), request.resolutionNotes());
        claims.flush();

        audit.record("WARRANTY_CLAIM_STATUS_UPDATED", "WARRANTY_CLAIM", claimId,
                Map.of("status", claim.getStatus().name()));
        return WarrantyClaimResponse.from(claim);
    }

    public WarrantyClaimResponse get(UUID id) {
        return WarrantyClaimResponse.from(claims.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Warranty claim not found.")));
    }

    public List<WarrantyClaimResponse> listByWarranty(UUID warrantyId) {
        if (!warranties.existsById(warrantyId)) {
            throw new ResourceNotFoundException("Warranty not found.");
        }
        return claims.findByWarrantyIdOrderByCreatedAtDesc(warrantyId)
                .stream().map(WarrantyClaimResponse::from).toList();
    }

    public PageResponse<WarrantyClaimResponse> listByStatus(WarrantyClaimStatus status, Pageable pageable) {
        if (status != null) {
            return PageResponse.from(claims.findByStatus(status, pageable).map(WarrantyClaimResponse::from));
        }
        return PageResponse.from(claims.findAll(pageable).map(WarrantyClaimResponse::from));
    }
}
