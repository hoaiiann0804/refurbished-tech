package com.example.refurbished.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthExchangeRequest(@NotBlank @Size(max = 200) String code) {}
