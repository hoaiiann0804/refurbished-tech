package com.example.refurbished.security.dto;

import com.example.refurbished.security.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String displayName,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotNull UserRole role) {}
