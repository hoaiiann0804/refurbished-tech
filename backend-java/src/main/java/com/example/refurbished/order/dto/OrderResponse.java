package com.example.refurbished.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.example.refurbished.order.Order;
import com.example.refurbished.order.OrderStatus;

// DTO trả về thông tin đơn hàng cho API client
public record OrderResponse(
        UUID id,
        UUID customerId,              // có thể null
        String customerName,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<OrderItemResponse> items // đã được convert + sắp xếp
) {
    // Constructor tuỳ chỉnh: tạo response khi không cần customerId
    public OrderResponse(UUID id, String customerName, OrderStatus status,
        BigDecimal totalAmount, Instant createdAt, List<OrderItemResponse> items) {
        this(id, null, customerName, status, totalAmount, createdAt, items);
    }

    // Convert Order (DB) → OrderResponse (DTO)
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomer() != null ? order.getCustomer().getId() : null,
                order.getCustomerName(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                // Convert items: OrderItem → OrderItemResponse, rồi sắp xếp theo deviceUnitId
                order.getItems().stream()
                        .map(OrderItemResponse::from)
                        .sorted(Comparator.comparing(OrderItemResponse::deviceUnitId))
                        .toList()
        );
    }
}
