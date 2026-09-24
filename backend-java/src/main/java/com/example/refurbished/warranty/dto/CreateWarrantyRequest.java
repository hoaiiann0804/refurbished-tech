package com.example.refurbished.warranty.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateWarrantyRequest(
        @NotNull UUID deviceUnitId,
        @Min(1) @Max(36) int durationMonths) {
}
