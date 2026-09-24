package com.example.refurbished.inventory.dto;

import com.example.refurbished.inventory.ConditionGrade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CompleteInspectionRequest(
        @NotNull Boolean passed,
        ConditionGrade grade,
        @Min(0) @Max(100) Integer batteryHealth,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal salePrice,
        @NotBlank @Size(max = 2000) String inspectionNotes,
        @Size(max = 1000) String batteryHealthUnavailableReason) {
}
