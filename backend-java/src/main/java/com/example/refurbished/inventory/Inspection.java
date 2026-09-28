package com.example.refurbished.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "inspections")
public class Inspection {

    // Một bản ghi kiểm định chất lượng của một thiết bị refurbished.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Thiết bị được kiểm tra; không cho sửa sau khi tạo để giữ tính lịch sử.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_unit_id", nullable = false, updatable = false)
    private DeviceUnit deviceUnit;

    // Người kiểm tra thực hiện đánh giá.
    @Column(name = "inspector_id", updatable = false)
    private UUID inspectorId;

    // Kết quả đạt/không đạt theo tiêu chuẩn kiểm định.
    @Column(nullable = false, updatable = false)
    private boolean passed;

    // Mức chất lượng sau khi kiểm tra: A/B/C hoặc tương đương.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 1, updatable = false)
    private ConditionGrade grade;

    // Tỷ lệ pin nếu đo được.
    @Column(name = "battery_health", updatable = false)
    private Integer batteryHealth;

    // Lý do không đo được pin (nếu có).
    @Column(name = "battery_health_unavailable_reason", length = 1000, updatable = false)
    private String batteryHealthUnavailableReason;

    // Giá bán đề xuất sau khi đã kiểm định.
    @Column(name = "sale_price", precision = 14, scale = 2, updatable = false)
    private BigDecimal salePrice;

    // Ghi chú kỹ thuật / nhận xét từ người kiểm tra.
    @Column(nullable = false, length = 2000, updatable = false)
    private String notes;

    // Thời điểm tạo bản ghi; dùng để audit và truy vết.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Inspection() {}

    // Constructor dùng khi tạo mới một buổi kiểm định.
    public Inspection(DeviceUnit deviceUnit, UUID inspectorId, boolean passed, ConditionGrade grade,
                      Integer batteryHealth, String batteryHealthUnavailableReason,
                      BigDecimal salePrice, String notes) {
        this.deviceUnit = deviceUnit;
        this.inspectorId = inspectorId;
        this.passed = passed;
        this.grade = grade;
        this.batteryHealth = batteryHealth;
        this.batteryHealthUnavailableReason = batteryHealthUnavailableReason;
        this.salePrice = salePrice;
        this.notes = notes;
    }

    // Gán thời gian tạo tự động khi insert vào DB.
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public DeviceUnit getDeviceUnit() { return deviceUnit; }
    public UUID getInspectorId() { return inspectorId; }
    public boolean isPassed() { return passed; }
    public ConditionGrade getGrade() { return grade; }
    public Integer getBatteryHealth() { return batteryHealth; }
    public String getBatteryHealthUnavailableReason() { return batteryHealthUnavailableReason; }
    public BigDecimal getSalePrice() { return salePrice; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
}
