package com.example.refurbished.customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(length = 320)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Customer() {}

    public Customer(String phoneNumber, String fullName, String email) {
        setPhoneNumber(phoneNumber);
        setFullName(fullName);
        setEmail(email);
    }

    public void update(String fullName, String email) {
        setFullName(fullName);
        setEmail(email);
    }

    public void setPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("^[0-9+]{8,20}$")) {
            throw new IllegalArgumentException("Phone number must contain 8 to 20 digits or + sign.");
        }
        this.phoneNumber = phoneNumber.trim();
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 200) {
            throw new IllegalArgumentException("Full name is required and must not exceed 200 characters.");
        }
        this.fullName = fullName.trim();
    }

    public void setEmail(String email) {
        this.email = (email == null || email.isBlank()) ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
