package com.example.refurbished.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String brand,
        @Size(max = 2000) String specificationSummary,
        @NotNull Boolean active) {

    public UpdateProductRequest {
        name = name == null ? null : name.trim();
        brand = brand == null ? null : brand.trim();
        specificationSummary = specificationSummary == null ? null : specificationSummary.trim();
    }
}
