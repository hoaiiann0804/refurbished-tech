package com.example.refurbished.order;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "sales_orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_name", nullable = false, length = 200)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(String customerName) {
        if (customerName == null || customerName.isBlank() || customerName.trim().length() > 200) {
            throw new IllegalArgumentException("Customer name is required and must not exceed 200 characters.");
        }
        this.customerName = customerName.trim();
        this.status = OrderStatus.COMPLETED;
        this.totalAmount = BigDecimal.ZERO;
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
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
