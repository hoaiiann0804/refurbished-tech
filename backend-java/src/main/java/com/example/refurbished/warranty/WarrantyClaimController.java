package com.example.refurbished.warranty;

import com.example.refurbished.common.api.PageResponse;
import com.example.refurbished.warranty.dto.CreateWarrantyClaimRequest;
import com.example.refurbished.warranty.dto.UpdateWarrantyClaimStatusRequest;
import com.example.refurbished.warranty.dto.WarrantyClaimResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class WarrantyClaimController {

    private final WarrantyClaimService service;

    public WarrantyClaimController(WarrantyClaimService service) {
        this.service = service;
    }

    @PostMapping("/warranties/{warrantyId}/claims")
    public ResponseEntity<WarrantyClaimResponse> create(
            @PathVariable UUID warrantyId,
            @Valid @RequestBody CreateWarrantyClaimRequest request) {
        WarrantyClaimResponse claim = service.create(warrantyId, request);
        return ResponseEntity.created(URI.create("/api/warranty-claims/" + claim.id())).body(claim);
    }

    @GetMapping("/warranties/{warrantyId}/claims")
    public List<WarrantyClaimResponse> listByWarranty(@PathVariable UUID warrantyId) {
        return service.listByWarranty(warrantyId);
    }

    @GetMapping("/warranty-claims/{id}")
    public WarrantyClaimResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/warranty-claims/{id}/status")
    public WarrantyClaimResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWarrantyClaimStatusRequest request) {
        return service.updateStatus(id, request);
    }

    @GetMapping("/warranty-claims")
    public PageResponse<WarrantyClaimResponse> list(
            @RequestParam(required = false) WarrantyClaimStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listByStatus(status, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }
}
