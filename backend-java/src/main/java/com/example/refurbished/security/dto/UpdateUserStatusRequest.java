package com.example.refurbished.security.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull(message = "enabled is required.")
        Boolean enabled
) {}
