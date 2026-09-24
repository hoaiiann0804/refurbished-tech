package com.example.refurbished.inventory.dto;

import com.example.refurbished.inventory.ConditionGrade;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

public record CreateDeviceUnitRequest(
        @NotNull UUID productId,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z0-9][A-Z0-9._/-]*") String serialNumber,
        ConditionGrade grade,
        @Min(0) @Max(100) Integer batteryHealth,
        @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal salePrice) {

    public CreateDeviceUnitRequest {
        serialNumber = serialNumber == null ? null : serialNumber.trim().toUpperCase(Locale.ROOT);
    }
}
