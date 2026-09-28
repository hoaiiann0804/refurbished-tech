package com.example.refurbished.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CheckoutRequest(
        @NotBlank @Size(max = 200) String customerName,
        @Size(max = 20) String phoneNumber,
        @Size(max = 320) String email,
        @NotEmpty @Size(max = 20) List<@NotNull UUID> deviceUnitIds) {

    public CheckoutRequest(String customerName, List<UUID> deviceUnitIds) {
        this(customerName, null, null, deviceUnitIds);
    }
}
