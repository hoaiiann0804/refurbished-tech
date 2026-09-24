package com.example.refurbished.security.dto;

import com.example.refurbished.security.AppUser;
import com.example.refurbished.security.UserRole;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName,
        UserRole role, boolean enabled, Instant createdAt) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
