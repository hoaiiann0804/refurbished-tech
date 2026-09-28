package com.example.refurbished.order.dto;

import com.example.refurbished.order.Order;
import com.example.refurbished.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Danh sách không tải items: tránh N+1 và payload lớn; chi tiết đơn có endpoint riêng.
public record OrderSummaryResponse(UUID id, String customerName, OrderStatus status,
        BigDecimal totalAmount, Instant createdAt) {
    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(order.getId(), order.getCustomerName(), order.getStatus(),
                order.getTotalAmount(), order.getCreatedAt());
    }
}
