package com.example.refurbished.warranty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWarrantyClaimRequest(
        @NotBlank(message = "Reported issue is required.")
        @Size(max = 1000, message = "Reported issue must not exceed 1000 characters.")
        String reportedIssue
) {}
