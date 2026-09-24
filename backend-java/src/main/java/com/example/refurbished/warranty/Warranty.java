package com.example.refurbished.warranty;

import com.example.refurbished.inventory.DeviceUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "warranties")
public class Warranty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_unit_id", nullable = false, unique = true, updatable = false)
    private DeviceUnit deviceUnit;

    @Column(name = "duration_months", nullable = false, updatable = false)
    private int durationMonths;

    @Column(name = "starts_on", nullable = false, updatable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false, updatable = false)
    private LocalDate endsOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Warranty() {
    }

    public Warranty(DeviceUnit deviceUnit, int durationMonths, LocalDate startsOn) {
        if (deviceUnit == null) {
            throw new IllegalArgumentException("Device unit is required.");
        }
        if (durationMonths < 1 || durationMonths > 36) {
            throw new IllegalArgumentException("Warranty duration must be between 1 and 36 months.");
        }
        if (startsOn == null) {
            throw new IllegalArgumentException("Warranty start date is required.");
        }
        this.deviceUnit = deviceUnit;
        this.durationMonths = durationMonths;
        this.startsOn = startsOn;
        this.endsOn = startsOn.plusMonths(durationMonths);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public DeviceUnit getDeviceUnit() { return deviceUnit; }
    public int getDurationMonths() { return durationMonths; }
    public LocalDate getStartsOn() { return startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public Instant getCreatedAt() { return createdAt; }
}
