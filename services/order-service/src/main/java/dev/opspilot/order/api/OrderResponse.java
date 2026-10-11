package dev.opspilot.order.api;

import java.time.Instant;

import dev.opspilot.order.order.Order;
import dev.opspilot.order.order.OrderStatus;

public record OrderResponse(
        Long id, String sku, int quantity, OrderStatus status, String failureReason, Instant createdAt) {

    static OrderResponse from(Order order) {
        return new OrderResponse(order.getId(), order.getSku(), order.getQuantity(), order.getStatus(),
                order.getFailureReason(), order.getCreatedAt());
    }
}
