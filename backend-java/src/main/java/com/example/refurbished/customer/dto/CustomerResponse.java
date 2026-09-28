package com.example.refurbished.customer.dto;

import com.example.refurbished.customer.Customer;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String phoneNumber,
        String fullName,
        String email,
        Instant createdAt,
        Instant updatedAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getPhoneNumber(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
