package com.example.refurbished.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Z0-9][A-Z0-9._/-]*") String modelCode,
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 100) String brand,
        @Size(max = 2000) String specificationSummary,
        Boolean active) {

    public CreateProductRequest {
        modelCode = modelCode == null ? null : modelCode.trim().toUpperCase(Locale.ROOT);
        name = name == null ? null : name.trim();
        brand = brand == null ? null : brand.trim();
        specificationSummary = specificationSummary == null ? null : specificationSummary.trim();
    }
}
