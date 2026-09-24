package com.example.refurbished.order.dto;

import com.example.refurbished.order.OrderItem;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID id, UUID deviceUnitId, String serialNumber,
        UUID productId, BigDecimal unitPrice) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getDeviceUnit().getId(),
                item.getDeviceUnit().getSerialNumber(), item.getDeviceUnit().getProduct().getId(),
                item.getUnitPrice());
    }
}
