package com.example.refurbished.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotBlank @Size(max = 200) String customerName,
        @NotEmpty @Size(max = 20) List<@NotNull UUID> deviceUnitIds) {
}
