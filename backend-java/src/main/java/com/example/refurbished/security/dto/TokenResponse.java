package com.example.refurbished.security.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds,
        UserResponse user) {}
