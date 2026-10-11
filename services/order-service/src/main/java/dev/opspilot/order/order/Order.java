package dev.opspilot.order.order;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    public static final int MAX_FAILURE_REASON = 512;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    @Column(length = MAX_FAILURE_REASON)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    protected Order() {
        // for JPA
    }

    public Order(String sku, int quantity) {
        this.sku = sku;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
        this.failureReason = null;
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.failureReason = truncate(reason);
    }

    public void fail(String reason) {
        this.status = OrderStatus.FAILED;
        this.failureReason = truncate(reason);
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String truncate(String reason) {
        if (reason == null || reason.length() <= MAX_FAILURE_REASON) {
            return reason;
        }
        return reason.substring(0, MAX_FAILURE_REASON);
    }
}
