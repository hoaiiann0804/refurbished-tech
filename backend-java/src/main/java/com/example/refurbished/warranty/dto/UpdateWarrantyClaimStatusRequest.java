package com.example.refurbished.warranty.dto;

import com.example.refurbished.warranty.WarrantyClaimStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateWarrantyClaimStatusRequest(
        @NotNull(message = "Status is required.")
        WarrantyClaimStatus status,

        @Size(max = 2000, message = "Resolution notes must not exceed 2000 characters.")
        String resolutionNotes
) {}
