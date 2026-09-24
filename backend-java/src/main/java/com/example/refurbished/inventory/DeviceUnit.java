package com.example.refurbished.inventory;

import com.example.refurbished.product.Product;
import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.InvalidInspectionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "device_units")
public class DeviceUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100, updatable = false)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 1)
    private ConditionGrade grade;

    @Column(name = "battery_health")
    private Integer batteryHealth;

    @Column(name = "battery_health_unavailable_reason", length = 1000)
    private String batteryHealthUnavailableReason;

    @Column(name = "sale_price", precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "inspection_passed")
    private Boolean inspectionPassed;

    @Column(name = "inspection_notes", length = 2000)
    private String inspectionNotes;

    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceUnit() {
    }

    public DeviceUnit(Product product, String serialNumber, ConditionGrade grade,
            Integer batteryHealth, BigDecimal salePrice) {
        this.product = product;
        this.serialNumber = serialNumber;
        this.grade = grade;
        this.batteryHealth = batteryHealth;
        this.salePrice = salePrice;
        this.status = DeviceStatus.RECEIVED;
    }

    public void startInspection() {
        requireStatus(DeviceStatus.RECEIVED, "start inspection");
        status = DeviceStatus.INSPECTING;
    }

    public void completeInspection(boolean passed, ConditionGrade grade, Integer batteryHealth,
            BigDecimal salePrice, String inspectionNotes, String batteryHealthUnavailableReason) {
        requireStatus(DeviceStatus.INSPECTING, "complete inspection");
        String notes = trimmedOrNull(inspectionNotes);
        String reason = trimmedOrNull(batteryHealthUnavailableReason);

        // Validate every rule before mutating the entity, including for non-HTTP callers.
        if (notes == null || notes.length() > 2000) {
            throw new InvalidInspectionException("Inspection notes are required and must not exceed 2000 characters.");
        }
        if (batteryHealth != null && (batteryHealth < 0 || batteryHealth > 100)) {
            throw new InvalidInspectionException("Battery health must be between 0 and 100.");
        }
        if (reason != null && (reason.length() > 1000 || batteryHealth != null)) {
            throw new InvalidInspectionException("Provide a battery reason only when battery health is unavailable, up to 1000 characters.");
        }
        if (salePrice != null && (salePrice.signum() <= 0 || salePrice.scale() > 2
                || salePrice.precision() - salePrice.scale() > 12)) {
            throw new InvalidInspectionException("Sale price must be positive with at most 12 integer and 2 decimal digits.");
        }
        if (passed && (grade == null || salePrice == null)) {
            throw new InvalidInspectionException("A passing inspection requires a grade and sale price.");
        }
        if (passed && batteryHealth == null && reason == null) {
            throw new InvalidInspectionException("A passing inspection requires battery health or a reason why it is unavailable.");
        }

        this.grade = grade;
        this.batteryHealth = batteryHealth;
        this.batteryHealthUnavailableReason = reason;
        this.salePrice = salePrice;
        this.inspectionNotes = notes;
        this.inspectionPassed = passed;
        this.inspectedAt = Instant.now();
        this.status = passed ? DeviceStatus.AVAILABLE : DeviceStatus.REJECTED;
    }

    public void reserveForCheckout() {
        requireStatus(DeviceStatus.AVAILABLE, "reserve for checkout");
        status = DeviceStatus.RESERVED;
    }

    public void completeSale() {
        requireStatus(DeviceStatus.RESERVED, "complete sale");
        status = DeviceStatus.SOLD;
    }

    private void requireStatus(DeviceStatus required, String action) {
        if (status != required) {
            throw new BusinessConflictException("Cannot " + action + " when device status is " + status + ".");
        }
    }

    private String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
    public Product getProduct() { return product; }
    public String getSerialNumber() { return serialNumber; }
    public DeviceStatus getStatus() { return status; }
    public ConditionGrade getGrade() { return grade; }
    public Integer getBatteryHealth() { return batteryHealth; }
    public String getBatteryHealthUnavailableReason() { return batteryHealthUnavailableReason; }
    public BigDecimal getSalePrice() { return salePrice; }
    public Boolean getInspectionPassed() { return inspectionPassed; }
    public String getInspectionNotes() { return inspectionNotes; }
    public Instant getInspectedAt() { return inspectedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
