package com.example.refurbished.product.dto;

import com.example.refurbished.product.Product;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(UUID id, String modelCode, String name, String brand,
        String specificationSummary, boolean active, Instant createdAt, Instant updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getModelCode(), product.getName(),
                product.getBrand(), product.getSpecificationSummary(), product.isActive(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
