package com.example.refurbished.customer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCustomerRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 200, message = "Full name must not exceed 200 characters.")
        String fullName,

        @Email(message = "Email must be valid.")
        @Size(max = 320, message = "Email must not exceed 320 characters.")
        String email
) {}
