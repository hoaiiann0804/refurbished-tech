package com.example.refurbished.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "model_code", nullable = false, unique = true, length = 64, updatable = false)
    private String modelCode;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(name = "specification_summary", length = 2000)
    private String specificationSummary;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // JPA requires a no-argument constructor.
    }

    public Product(String modelCode, String name, String brand, String specificationSummary, boolean active) {
        this.modelCode = modelCode;
        updateDetails(name, brand, specificationSummary, active);
    }

    public void updateDetails(String name, String brand, String specificationSummary, boolean active) {
        this.name = name;
        this.brand = brand;
        this.specificationSummary = specificationSummary;
        this.active = active;
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
    public String getModelCode() { return modelCode; }
    public String getName() { return name; }
    public String getBrand() { return brand; }
    public String getSpecificationSummary() { return specificationSummary; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
