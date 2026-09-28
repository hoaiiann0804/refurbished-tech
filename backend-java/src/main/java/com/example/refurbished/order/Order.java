package com.example.refurbished.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.example.refurbished.inventory.DeviceUnit;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "sales_orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;
    // Nhiều đơn hành có thể thuộc về  một khách hàng
    // LAZY: khi đọc đơn hàng, Hibernate chưa tải thông tin khách ngay; chỉ truy vấn khi gọi order.getCustomer(). Mục tiêu kà giảm truy vấn dư
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "customer_id")
    private com.example.refurbished.customer.Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    //Một đơn có nhiều dòng hàng.
    //Lưu/xóa Order sẽ tự áp dụng cho các OrderItem; item bị khỏi danh sách cũng bị xóa DB 
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }
    // Kiểm tra tên khách hàng hợp lệ 
    // Khởi tạo Order với trạng thái COMPLETED và tổng tiền = 0 
    public Order(String customerName, com.example.refurbished.customer.Customer customer) {
        if (customerName == null || customerName.isBlank() || customerName.trim().length() > 200) {
            throw new IllegalArgumentException("Customer name is required and must not exceed 200 characters.");
        }
        this.customerName = customerName.trim();
        this.customer = customer;
        this.status = OrderStatus.COMPLETED;
        this.totalAmount = BigDecimal.ZERO;
    }

    public Order(String customerName) {
        this(customerName, null);
    }

    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new com.example.refurbished.common.exception.BusinessConflictException("Order is already cancelled.");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void addSoldDevice(DeviceUnit device) {
        BigDecimal price = device.getSalePrice();
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("A sold device must have a positive sale price.");
        }
        items.add(new OrderItem(this, device, price));
        totalAmount = totalAmount.add(price);
    }

    @PrePersist
    void onCreate() {
        // PostgreSQL lưu microsecond: chuẩn hóa trước khi trả response để retry và
        // bộ lọc from/to không sai khác với thời điểm thực sự lưu trong database.
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public com.example.refurbished.customer.Customer getCustomer() { return customer; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
