package com.example.refurbished.security.dto;

import com.example.refurbished.security.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull(message = "role is required.")
        UserRole role
) {}
