package com.example.refurbished.order;

import com.example.refurbished.inventory.DeviceUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_unit_id", nullable = false, unique = true, updatable = false)
    private DeviceUnit deviceUnit;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2, updatable = false)
    private BigDecimal unitPrice;

    protected OrderItem() {
    }

    OrderItem(Order order, DeviceUnit deviceUnit, BigDecimal unitPrice) {
        this.order = order;
        this.deviceUnit = deviceUnit;
        this.unitPrice = unitPrice;
    }

    public UUID getId() { return id; }
    public DeviceUnit getDeviceUnit() { return deviceUnit; }
    public BigDecimal getUnitPrice() { return unitPrice; }
}
