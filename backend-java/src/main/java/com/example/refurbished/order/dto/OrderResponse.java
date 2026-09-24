package com.example.refurbished.order.dto;

import com.example.refurbished.order.Order;
import com.example.refurbished.order.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID id, String customerName, OrderStatus status,
        BigDecimal totalAmount, Instant createdAt, List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getCustomerName(), order.getStatus(),
                order.getTotalAmount(), order.getCreatedAt(), order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .toList());
    }
}
