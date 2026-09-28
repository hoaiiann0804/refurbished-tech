package com.example.refurbished.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.example.refurbished.common.exception.BusinessConflictException;
import com.example.refurbished.common.exception.InvalidInspectionException;
import com.example.refurbished.product.Product;

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

@Entity
@Table(name = "device_units")
public class DeviceUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Mỗi DeviceUnit phải thuộc về 1 Product; Product chỉ được load khi cần )(Lazy loading)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    // Mỗi serial là định danh duy nhất của thiết bị.
    // Không được null, khônng trùng và không có phép thay đổi sau khi tạo 
    @Column(name = "serial_number", nullable = false, unique = true, length = 100, updatable = false)
    private String serialNumber;

    // Trạng thái hiện tại của thiết bị trong quy trình kho.
    // Ví dụ: RECEIVED, INSPECTING, AVAILABLE, REJECTED, RESERVED, SOLD...
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceStatus status;

    // Đánh giá chất lượng sau khi kiểm tra
    // Dùng String trong DB để dễ đọc và lưu trữ các giá trị enum 
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 1)
    private ConditionGrade grade;

    // Tình trạng pin của thiết bị,nếu có dữ liệu đo được
    @Column(name = "battery_health")
    private Integer batteryHealth;
    
    // Lý do pin không thể đo được hoặc không có sẵn
    @Column(name = "battery_health_unavailable_reason", length = 1000)
    private String batteryHealthUnavailableReason;
    
    //Giá ban đề xuất hoặc giá cuối cùng của thiết bị.

    @Column(name = "sale_price", precision = 14, scale = 2)
    private BigDecimal salePrice;

    // Kết quả kiểm tra: pass hay fail
    @Column(name = "inspection_passed")
    private Boolean inspectionPassed;

    // Ghi chú chi tiết trong quá trình kiểm tra 
    @Column(name = "inspection_notes", length = 2000)
    private String inspectionNotes;
    // Thời điểm hoàn thành kiểm tra
    @Column(name = "inspected_at")
    private Instant inspectedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeviceUnit() {
    }
    // Khi mới tạo thiết bị ở trạng thái RECEIVED  
    public DeviceUnit(Product product, String serialNumber, ConditionGrade grade,
            Integer batteryHealth, BigDecimal salePrice) {
        this.product = product;
        this.serialNumber = serialNumber;
        this.grade = grade;
        this.batteryHealth = batteryHealth;
        this.salePrice = salePrice;
        this.status = DeviceStatus.RECEIVED;
    }
    // Bắt đầu quy trình kiểm tra.
    // Chỉ cho phép khi thiết bị đang ở trạng thái RECEIVED hoặc IN_REPAIR 
    public void startInspection() {
        if (status != DeviceStatus.RECEIVED && status != DeviceStatus.IN_REPAIR) {
            throw new BusinessConflictException("Cannot start inspection when device status is " + status + ".");
        }
        status = DeviceStatus.INSPECTING;
    }

    // Chuyển thiết bị sang sửa chửa sau khi bị từ chối 
    // Chỉ được phép khi trạng thái hiện tại là REJECTED
    public void sendToRepair() {
        requireStatus(DeviceStatus.REJECTED, "send to repair");
        status = DeviceStatus.IN_REPAIR;
    }

    // Hoàn tất kiểm tra.
    // Nếu pass -> thiết bị có thể đưa vào kho để bán 
    // Nếu fail -> thiết bị từ chối và có thể chuyển sang sửa chữa
    public void completeInspection(boolean passed, ConditionGrade grade, Integer batteryHealth,
            BigDecimal salePrice, String inspectionNotes, String batteryHealthUnavailableReason) {
        //Bắc buộc phải ở đang trạng thái đang kiểm tra
        requireStatus(DeviceStatus.INSPECTING, "complete inspection");
        String notes = trimmedOrNull(inspectionNotes);
        String reason = trimmedOrNull(batteryHealthUnavailableReason);

        // Kiểm tra toàn bộ quy tắc nghiệp vụ trước khi thay đổi đối tượng
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

    // Đặt chỗ trước khi khách hàng thanh toán
    // Chỉ khi thiết bị đang AVAILABLE
    public void reserveForCheckout() {
        requireStatus(DeviceStatus.AVAILABLE, "reserve for checkout");
        status = DeviceStatus.RESERVED;
    }

    // Hoàn tất bán hàng
    // Chỉ cho phép nếu đã được reserve trước đó. 

    public void completeSale() {
        requireStatus(DeviceStatus.RESERVED, "complete sale");
        status = DeviceStatus.SOLD;
    }
    // Hủy đặt chỗ nếu khách không mua nữa 
    // Chỉ luồng giữ chỗ được trả máy RESERVED về kho; refund không gọi phương thức này.
    public void releaseReservation() {
        requireStatus(DeviceStatus.RESERVED, "release reservation");
        status = DeviceStatus.AVAILABLE;
    }

    // Trả thiết bị bán/ giữ chỗ về lại kho.
    // Dùng khi có hoàn hàng hoặc hủy giao dịch 
    public void returnToInventory() {
        if (status != DeviceStatus.SOLD && status != DeviceStatus.RESERVED) {
            throw new BusinessConflictException("Cannot return device to inventory when status is " + status + ".");
        }
        status = DeviceStatus.AVAILABLE;
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
